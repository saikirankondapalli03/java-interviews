# Component 02: SNS + SQS (Eventing) — Question & Answer Bank

**Reference:** `components/02-SNS-SQS-EVENTING.md`  
**Interview coverage:** Intro, architecture, technical depth, scenarios, follow-ups, evaluation.

---

## 1. Intro & Context (Opening)

### Q1.1 What is the role of SNS and SQS in the file processing flow?

**Answer:** S3 publishes “object created” to SNS; SNS fans out to SQS. Consumers (Lambda or Step Functions) pull from SQS and trigger the Spark job. This decouples “file arrived” from “process” and gives retries + DLQ.

---

### Q1.2 Why SNS → SQS instead of S3 → SQS directly?

**Answer:** S3 can publish directly to SQS. We use SNS in the middle for: **fan-out** (multiple queues or consumers), **filtering** by prefix/attributes, **flexibility** to add subscribers (EventBridge, Lambda for metrics) without changing S3. If the design is one queue and one consumer, S3 → SQS is fine; say “we used SNS for fan-out and future flexibility.”

---

## 2. Architecture & Ownership

### Q2.1 What do you put in the SQS message body and attributes?

**Answer:** **Body:** At least `bucket`, `key`, `file_size`, `event_time`. Optional `etag` for idempotency. **Attributes:** Use message attributes for `file_type` (positions vs mtm_pnl) from key prefix so the orchestrator doesn’t parse the key every time.

---

### Q2.2 Why not invoke Glue directly from the S3 event (Lambda)?

**Answer:** Then we have no **queue**: if Lambda fails before invoking Glue we lose the trigger. SQS gives **durability** and **retries** without custom code. Flow: S3 → SNS → SQS → Lambda/Step Functions → Glue.

---

## 3. Technical Depth (Pain Points)

### Q3.1 What is visibility timeout and why is it critical?

**Answer:** After a consumer receives a message it’s hidden for the **visibility timeout**. If the consumer doesn’t delete it before timeout the message becomes visible again (can be processed again). **Pain point:** If visibility timeout is **shorter** than Spark job duration the message reappears and another worker might start a second job for the same file. So **visibility timeout must be ≥ max expected job duration** (e.g. 1–2 hours). Or: orchestrator deletes the message as soon as it has submitted the job (idempotency protects if we crash after submit). **Interview answer:** “We set visibility timeout longer than max Glue job duration. Once the orchestrator submits the job it can delete the message. Idempotency in DynamoDB ensures that if the same file is processed twice we don’t duplicate data.”

---

### Q3.2 How do you configure retries and DLQ?

**Answer:** **Max receives:** After N failed processing attempts SQS moves the message to **DLQ**. We set N (e.g. 3) so transient failures get retries but poison messages don’t block the queue. **DLQ handling:** Ops alerted (CloudWatch alarm on DLQ depth). Inspect message (bucket, key), fix file or path, re-drive or trigger manual run.

---

### Q3.3 Standard vs FIFO? How do you limit concurrency?

**Answer:** We use **Standard** for throughput; per-file correctness via idempotency. FIFO would add ordering but throughput limits. **Consumers:** Limit Lambda/Step Functions concurrency (e.g. reserved concurrency 1–2) so we don’t run too many Glue jobs at once. SQS depth = backpressure; monitor it.

---

## 4. Scenario & Design

### Q4.1 At-least-once: how do you avoid duplicate processing?

**Answer:** Idempotency. Before starting Glue we check DynamoDB. If this file is already SUCCESS we skip (and delete the SQS message). Second run sees SUCCESS and skips. Data layer can also overwrite by file_date.

---

### Q4.2 What’s in the SQS message? Is it enough for the orchestrator?

**Answer:** Bucket, key, size, event time; optionally etag. Enough for orchestrator to pass to Glue: `--input-path`, `--file-type`, `--file-date`.

---

## 5. Data Engineering Metrics (SNS/SQS)

### Q5.1 What SQS metrics do you track and why?

**Answer:**

| Metric | What to track | Why |
|--------|----------------|-----|
| **Messages in queue** | ApproximateNumberOfMessagesVisible | Backlog; downstream slow if growing. |
| **DLQ depth** | Same for DLQ | **Alert if > 0**—failures; ops must act. |
| **Age of oldest message** | ApproximateAgeOfOldestMessage | Stale work; SLA risk. |

---

## 6. Evaluation Cheat Sheet

| Area | What good looks like | Red flag |
|------|----------------------|----------|
| **Visibility timeout** | Set ≥ max job duration; explains double-processing risk | Short timeout or doesn’t mention it |
| **DLQ** | Max receives; alarm on DLQ depth; re-drive process | No DLQ or no alert |
| **Message content** | Bucket, key, size, event_time; attributes for file_type | Vague “we pass the event” |
| **Decoupling** | SQS = durability, retries; idempotency for duplicates | “Lambda calls Glue directly from S3” |

Use this when they ask “how does the event get from S3 to the job?” or “how do you handle retries and failures?”
