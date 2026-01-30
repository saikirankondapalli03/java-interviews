# Component: Orchestrator (Lambda or Step Functions)

**Role in flow:** Consumes SQS message (bucket, key, file type, file date). Checks DynamoDB for idempotency. If not already SUCCESS, submits Glue (or EMR) job with args. Updates DynamoDB on job completion (via Glue callback or polling).

---

## 1. Interview Pain Points & Nuances

### Lambda vs Step Functions

| Aspect | Lambda | Step Functions |
|--------|--------|----------------|
| **Use when** | Simple: receive → check DynamoDB → start Glue → return. Job completion handled elsewhere (Glue notifies SNS, or separate process polls). | Multi-step: receive → check → start Glue → **wait** for Glue to finish → update DynamoDB → delete SQS message. Need to wait for long-running Glue. |
| **Wait for Glue** | Lambda can't wait hours. Lambda only "starts" the job; something else must update DynamoDB. | Step Functions can **wait** for Glue job completion (callback or poll) then update DynamoDB and delete message. One workflow, clear state. |
| **Interview answer** | "We use Lambda to read SQS, check DynamoDB, start Glue, and delete the message once job is submitted. Glue on completion publishes to SNS or updates DynamoDB; we may have a second Lambda that reacts to Glue completion." | "We use Step Functions so we can wait for the Glue job to finish, then update DynamoDB and delete the SQS message in the same workflow." |

**Pick one and be consistent.** For a 1-hour-deep interview, Step Functions is easier to explain end-to-end.

### Idempotency Check (Critical)

- **Before starting:** Read DynamoDB with idempotency key. If item exists and `status == SUCCESS`, **skip**: don't start Glue, delete SQS message. Return.
- **Conditional write:** Orchestrator can do **conditional put**: "insert audit row only if it doesn't exist." One worker wins; the other sees "already exists" and skips. Prevents two concurrent invocations from both starting the job.
- **Status transitions:** PENDING (job started) → SUCCESS or FAILED (job finished). Only allow transition from PENDING/FAILED to SUCCESS/FAILED; never overwrite SUCCESS with PENDING.

### Invoking Glue

- **API:** `glue.start_job_run(JobName=..., Arguments={'--input-path': s3_uri, '--file-type': file_type, '--file-date': file_date})`. Glue runs PySpark; arguments available as `getResolvedOptions` or `sys.argv`.
- **Async:** StartJobRun returns `JobRunId`. To wait: poll `get_job_run` or Step Functions "run job and wait for callback" (Glue sends task token when done) or EventBridge on Glue job state change.

### Concurrency and Errors

- **Limit Glue concurrency:** Use **reserved concurrency** on Lambda (e.g. 2) or semaphore so we don't start 100 Glue jobs at once.
- **If DynamoDB read fails:** Don't start job; don't delete message. Message returns to queue; retry.
- **If Glue StartJobRun fails:** Mark audit FAILED or leave PENDING and alert. Don't delete SQS message so we can retry, or send to DLQ after N receives.

---

## 2. Data Engineering Metrics (Orchestrator)

| Metric | What to track | How |
|--------|----------------|-----|
| **Invocations** | Lambda or Step Functions runs | CloudWatch. |
| **Throttles / errors** | Lambda throttled; Step Functions failed | Alarms. |
| **Glue jobs started per hour** | Throughput | Glue metrics or audit table count. |

---

## 3. Likely Interview Questions & Answers

- **How do you prevent two workers from processing the same file?** Idempotency key in DynamoDB. First worker does conditional put (only if not exists) to create PENDING; second sees existing row and skips.
- **Who updates the audit table when the job completes?** Either the Glue job itself (put_item SUCCESS/FAILED at end of script) or a Lambda triggered by EventBridge on Glue job state change. Glue has the idempotency key (passed as job arg).
- **Why not run the Spark code inside Lambda?** Lambda has 15 min max, 10 GB RAM. Our file can be millions of rows; we need Spark (Glue/EMR). Lambda is the **orchestrator**, not the **processor**.

Use this when they ask "who triggers the Glue job?" or "how do you avoid double submission?"
