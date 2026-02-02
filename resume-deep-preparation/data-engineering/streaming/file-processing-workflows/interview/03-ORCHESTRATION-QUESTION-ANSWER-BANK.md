# Component 03: Orchestrator (Lambda / Step Functions) — Question & Answer Bank

**Reference:** `components/03-ORCHESTRATION-LAMBDA-STEP-FUNCTIONS.md`  
**Interview coverage:** Intro, architecture, technical depth, scenarios, follow-ups, evaluation.

---

## 1. Intro & Context (Opening)

### Q1.1 What is the role of the orchestrator in the file processing flow?

**Answer:** Consumes SQS message (bucket, key, file type, file date). Checks DynamoDB for idempotency. If not already SUCCESS, submits Glue (or EMR) job with args. Updates DynamoDB on job completion (via Glue callback or polling). Optionally deletes SQS message after job submit or after completion.

---

### Q1.2 Why not run the Spark code inside Lambda?

**Answer:** Lambda has 15 min max, 10 GB RAM. Our file can be millions of rows; we need Spark (Glue/EMR). Lambda is the **orchestrator**, not the **processor**.

---

## 2. Architecture & Ownership

### Q2.1 When do you use Lambda vs Step Functions for orchestration?

**Answer:**

| Aspect | Lambda | Step Functions |
|--------|--------|----------------|
| **Use when** | Simple: receive → check DynamoDB → start Glue → return. Job completion handled elsewhere (Glue notifies SNS, or separate process polls). | Multi-step: receive → check → start Glue → **wait** for Glue to finish → update DynamoDB → delete SQS message. Need to wait for long-running Glue. |
| **Wait for Glue** | Lambda can’t wait hours. Lambda only “starts” the job; something else must update DynamoDB. | Step Functions can **wait** for Glue via EventBridge (Glue state change) → Lambda → SendTaskSuccess/SendTaskFailure; or poll get_job_run. One workflow, clear state. |
| **Interview** | “We use Lambda to read SQS, check DynamoDB, start Glue, and delete the message once job is submitted. Glue on completion publishes to SNS or updates DynamoDB; we may have a second Lambda that reacts to Glue completion.” | “We use Step Functions so we can wait for the Glue job to finish, then update DynamoDB and delete the SQS message in the same workflow.” |

Pick one and be consistent. For a deep interview, Step Functions is easier to explain end-to-end.

---

### Q2.2 How do you invoke Glue from the orchestrator?

**Answer:** API: `glue.start_job_run(JobName=..., Arguments={'--input-path': s3_uri, '--file-type': file_type, '--file-date': file_date})`. Glue runs PySpark; arguments available as `getResolvedOptions` or `sys.argv`. **Async:** StartJobRun returns JobRunId. To wait: poll `get_job_run`, or Step Functions “run job and wait for callback”—Glue does not send task tokens; use **EventBridge** (Glue job state change) → Lambda → SendTaskSuccess/SendTaskFailure with the task token (pass token in Glue job args or store in DynamoDB keyed by JobRunId).

---

## 3. Technical Depth (Pain Points)

### Q3.1 How do you implement the idempotency check? Before starting the job?

**Answer:** **Before starting:** Read DynamoDB with idempotency key. If item exists and `status == SUCCESS`, **skip**: don’t start Glue, delete SQS message, return. **Conditional write:** Orchestrator can do **conditional put**: “insert audit row only if it doesn’t exist.” One worker wins; the other sees “already exists” and skips. Prevents two concurrent invocations from both starting the job. **Status transitions:** PENDING (job started) → SUCCESS or FAILED (job finished). Only allow transition from PENDING/FAILED to SUCCESS/FAILED; never overwrite SUCCESS with PENDING.

---

### Q3.2 What if DynamoDB read fails? What if Glue StartJobRun fails?

**Answer:** **If DynamoDB read fails:** Don’t start job; don’t delete message. Message returns to queue; retry. **If Glue StartJobRun fails:** Mark audit FAILED or leave PENDING and alert. Don’t delete SQS message so we can retry, or send to DLQ after N receives.

---

### Q3.3 How do you limit Glue concurrency?

**Answer:** Use **reserved concurrency** on Lambda (e.g. 2) or a semaphore so we don’t start 100 Glue jobs at once. SQS depth gives backpressure; monitor it.

---

## 4. Scenario & Design

### Q4.1 How do you prevent two workers from processing the same file?

**Answer:** Idempotency key in DynamoDB. First worker does conditional put (only if not exists) to create PENDING; second sees existing row and skips.

---

### Q4.2 Who updates the audit table when the job completes?

**Answer:** Either the Glue job itself (put_item SUCCESS/FAILED at end of script) or a Lambda triggered by EventBridge on Glue job state change. Glue has the idempotency key (passed as job arg).

---

## 5. Data Engineering Metrics (Orchestrator)

### Q5.1 What orchestrator metrics do you track?

**Answer:**

| Metric | What to track | How |
|--------|----------------|-----|
| **Invocations** | Lambda or Step Functions runs | CloudWatch. |
| **Throttles / errors** | Lambda throttled; Step Functions failed | Alarms. |
| **Glue jobs started per hour** | Throughput | Glue metrics or audit table count. |

---

## 6. Evaluation Cheat Sheet

| Area | What good looks like | Red flag |
|------|----------------------|----------|
| **Idempotency** | Conditional put; skip if SUCCESS; status transitions | No conditional write; overwrite SUCCESS |
| **Lambda vs Step** | Clear choice; explains “wait for Glue” | “We use Lambda and wait for Glue” (Lambda can’t wait hours) |
| **Failure handling** | Don’t delete message on Glue start failure; DLQ | Delete message before job completes |
| **Concurrency** | Reserved concurrency or semaphore | Unbounded Glue starts |

Use this when they ask “who triggers the Glue job?” or “how do you avoid double submission?”
