# Component: SNS + SQS (Event-Driven Notification)

**Role in flow:** S3 publishes "object created" to SNS; SNS fans out to SQS. Consumers (Lambda or Step Functions) pull from SQS and trigger the Spark job. Decouples "file arrived" from "process" and gives retries + DLQ.

---

## 1. Interview Pain Points & Nuances

### Why SNS → SQS (not S3 → SQS directly?)

- **S3 can publish directly to SQS.** We use SNS in the middle to: **fan-out** (multiple queues or consumers), **filtering** by prefix/attributes, **flexibility** to add subscribers (EventBridge, Lambda for metrics) without changing S3. If design is one queue, one consumer, S3 → SQS is fine; say "we used SNS for fan-out and future flexibility."

### Message Content

- **Body:** At least `bucket`, `key`, `file_size`, `event_time`. Optional `etag` for idempotency.
- **Attributes:** Use message attributes for `file_type` (positions vs mtm_pnl) from key prefix so orchestrator doesn't parse key every time.

### Visibility Timeout (Critical)

- **What it is:** After a consumer receives a message, it's hidden for the **visibility timeout**. If the consumer doesn't delete it before timeout, the message becomes visible again (can be processed again).
- **Pain point:** If visibility timeout is **shorter** than Spark job duration, the message reappears and **another worker might start a second job** for the same file. So **visibility timeout must be ≥ max expected job duration** (e.g. 1–2 hours). Or: orchestrator deletes the message as soon as it has submitted the job (idempotency protects if we crash after submit).
- **Interview answer:** "We set visibility timeout longer than max Glue job duration. Once the orchestrator submits the job, it can delete the message. Idempotency in DynamoDB ensures that if the same file is processed twice, we don't duplicate data."

### Retries and DLQ

- **Max receives:** After N failed processing attempts, SQS moves the message to **DLQ**. We set N (e.g. 3) so transient failures get retries but poison messages don't block the queue.
- **DLQ handling:** Ops alerted (CloudWatch alarm on DLQ depth). Inspect message (bucket, key), fix file or path, re-drive or trigger manual run.

### Ordering and Scaling

- **FIFO vs Standard:** We use **Standard** for throughput; per-file correctness via idempotency. FIFO would add ordering but throughput limits.
- **Consumers:** Limit Lambda/Step Functions concurrency (e.g. reserved concurrency 1–2) so we don't run too many Glue jobs at once. SQS depth = backpressure; monitor it.

---

## 2. Data Engineering Metrics (SNS/SQS)

| Metric | What to track | Why |
|--------|----------------|-----|
| **Messages in queue** | ApproximateNumberOfMessagesVisible | Backlog; downstream slow if growing. |
| **DLQ depth** | Same for DLQ | **Alert:** > 0 means failures; ops must act. |
| **Age of oldest message** | ApproximateAgeOfOldestMessage | Stale work; SLA risk. |

---

## 3. Likely Interview Questions & Answers

- **Why not invoke Glue directly from S3 event (Lambda)?** We could, but then we have no **queue**: if Lambda fails before invoking Glue, we lose the trigger. SQS gives **durability** and **retries** without custom code. S3 → SNS → SQS → Lambda/Step Functions → Glue.

- **At-least-once: how do you avoid duplicate processing?** Idempotency. Before starting Glue we check DynamoDB. If this file is already SUCCESS, we skip (and delete the SQS message). Second run sees SUCCESS and skips. Data layer can also overwrite by file_date.

- **What's in the SQS message?** Bucket, key, size, event time; optionally etag. Enough for orchestrator to pass to Glue (--input-path, --file-type, --file-date).

Use this when they ask "how does the event get from S3 to the job?" or "how do you handle retries and failures?"
