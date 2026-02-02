# Component 01: S3 (Landing, Curated, Quarantine) — Question & Answer Bank

**Reference:** `components/01-S3.md`  
**Interview coverage:** Intro, architecture, technical depth, scenarios, follow-ups, evaluation.

---

## 1. Intro & Context (Opening)

### Q1.1 What is the role of S3 in the file processing flow?

**Answer:** S3 has three roles: **Landing** = where Equilend (or source) drops files; **Curated** = valid rows in Parquet; **Quarantine** = invalid rows for inspection. S3 events trigger the pipeline (ObjectCreated:Complete → SNS → SQS).

---

### Q1.2 Why do we use S3 event notifications instead of polling?

**Answer:** Polling adds latency and wastes cycles when no file arrives. Event-driven (S3 → SNS → SQS) gives near-immediate trigger and scales without polling loops. We only react when a file is actually written.

---

## 2. Architecture & Ownership

### Q2.1 Which S3 event do you use and why? Why not ObjectCreated:Put alone?

**Answer:** We use **`s3:ObjectCreated:Complete`** (or `s3:ObjectCreated:*` with filtering). We **do not** use `ObjectCreated:Put` alone because **multipart uploads** emit multiple events (one per part); only `Complete` fires when the full object is written. If we triggered on every part we’d process incomplete files.

---

### Q2.2 Are S3 event notifications ordered? At-least-once or exactly-once?

**Answer:** Events are **not** ordered; two files can trigger in any order. Delivery is **at least once**—you can get duplicate events for the same object. That’s why we have **idempotency** (DynamoDB check before running the Spark job). Our idempotency key is per file, so we don’t depend on order.

---

### Q2.3 Why use prefix/suffix filter on S3 events? Where do events go?

**Answer:** We restrict to `positions/` and `mtm_pnl/` so only Equilend files trigger the pipeline; reduces noise and cost. **Destination:** S3 can notify SNS, SQS, or Lambda. We use SNS → SQS to **decouple** and allow multiple consumers, retries, and DLQ. Lambda as direct target would require Lambda to push to SQS or invoke Glue—SNS→SQS is cleaner for “queue and process later.”

---

## 3. Technical Depth (Pain Points)

### Q3.1 Is S3 read-after-write consistent? What about multipart uploads?

**Answer:** S3 is **strongly consistent** for PUTs (since Dec 2020). Once `Complete` is returned, reads see the full object. No need to “wait a bit” before processing. For multipart uploads we only react on `Complete`, so we never process a partial upload.

---

### Q3.2 How do you design bucket and prefix layout for landing, curated, and quarantine?

**Answer:**  
- **Landing:** One bucket, prefixes by file type: `positions/`, `mtm_pnl/`. Easy to attach event rules per prefix and apply lifecycle/retention (e.g. delete or move to cold after 30 days).  
- **Curated:** Partitioned by source and date, e.g. `equilend/positions/file_date=YYYYMMDD/`. Enables partition pruning in Athena/Spark and clear lineage.  
- **Quarantine:** Same logical partition (e.g. `equilend/positions/20250128/`) plus reject reason in filename or manifest so ops can debug.

---

### Q3.3 What lifecycle and cost controls do you apply to each zone?

**Answer:**  
- **Landing:** Process once; **transition to Glacier** or **expire** after N days. Keep long enough for replay (e.g. 7–30 days).  
- **Curated:** Retain for analytics/compliance (e.g. 7 years); lifecycle to Glacier for old partitions.  
- **Quarantine:** Retain for ops to fix and replay; alert on object count so we don’t ignore failures.

---

### Q3.4 What security and compliance measures do you use for S3?

**Answer:** **Encryption:** SSE-S3 or SSE-KMS on all three buckets; KMS if you need audit of key usage. **Access:** Least privilege. Glue/EMR role: read landing + config; write curated + quarantine. No public access; block public access enabled.

---

## 4. Scenario & Design

### Q4.1 What if the same file is uploaded twice?

**Answer:** We get two events; both end up as SQS messages. Orchestrator checks DynamoDB with idempotency key; first run writes SUCCESS, second run sees SUCCESS and **skips** (or overwrites, depending on product requirement). No duplicate data in curated/Snowflake.

---

### Q4.2 How do you handle a file that’s still being written?

**Answer:** We only subscribe to `ObjectCreated:Complete`. For multipart uploads that event fires only when the entire object is committed. We never process in-flight uploads.

---

### Q4.3 “S3 used to be eventually consistent”—is that still true?

**Answer:** S3 is **strongly consistent** for overwrite PUTs and DELETEs since Dec 2020. So “read after write” is safe for our use case. No need to design around eventual consistency for new PUTs.

---

## 5. Data Engineering Metrics (S3)

### Q5.1 What S3-related metrics do you track and how?

**Answer:**

| Metric | What to track | How |
|--------|----------------|-----|
| **Files received** | Count of objects created per prefix per day | CloudWatch metrics from S3 (optional) or count SQS messages; compare to expected (e.g. 2 files/day). |
| **Object size** | Per file | In SQS message body (from event) or S3 metadata; use for Glue sizing and validation. |
| **Quarantine volume** | Object count or size in quarantine prefix | CloudWatch or S3 inventory; alarm if growing (indicates validation issues). |

---

## 6. Evaluation Cheat Sheet

| Area | What good looks like | Red flag |
|------|----------------------|----------|
| **Event type** | Uses Complete; explains multipart | Uses Put or doesn’t mention multipart |
| **Consistency** | Knows strong consistency (Dec 2020) | Says “eventual consistency” without nuance |
| **Prefix design** | Landing/curated/quarantine; partition by date | Flat bucket, no lifecycle |
| **Security** | SSE, least privilege, block public | No mention of encryption or access |

Use this bank with `01-S3.md` when they drill into “how does the file get detected?” or “how do you avoid processing incomplete files?”
