# Component 04: DynamoDB (Audit & Idempotency) — Question & Answer Bank

**Reference:** `components/04-DYNAMODB-AUDIT-IDEMPOTENCY.md`  
**Interview coverage:** Intro, architecture, technical depth, scenarios, follow-ups, evaluation.

---

## 1. Intro & Context (Opening)

### Q1.1 What is the role of DynamoDB in the file processing flow?

**Answer:** Store one row per file with idempotency key. Orchestrator checks before starting the job; Glue (or completion Lambda) updates on success/failure. Used for skip-on-replay and compliance/lineage. DynamoDB or PostgreSQL (RDS) both work; DynamoDB is serverless and avoids connection pool in Lambda.

---

### Q1.2 Why DynamoDB and not PostgreSQL (RDS) for audit and idempotency?

**Answer:** For *idempotency and audit only*, **PostgreSQL is perfectly fine**. You need: (1) a unique constraint on the idempotency key, (2) a conditional “insert only if not exists” (or “update only if status = PENDING”) so only one worker wins, (3) strong consistency on the read-before-start. PostgreSQL gives that with a simple table + unique index + `INSERT ... ON CONFLICT DO NOTHING` / conditional update. **Why we show DynamoDB:** serverless, no connection pool in Lambda (each invocation gets a new HTTP call to DynamoDB), auto-scaling, and same AWS-native story as S3/SQS/Glue. If your org already runs PostgreSQL you can use the same RDS for the audit table. **Interview:** “We use DynamoDB for audit and idempotency so Lambda doesn’t need a DB connection pool and we get serverless scaling. You could do the same with PostgreSQL: one table, unique constraint on the idempotency key, conditional insert/update so only one job runs per file.”

---

## 2. Architecture & Ownership

### Q2.1 How do you design the DynamoDB key for audit and idempotency?

**Answer:** **Partition key (PK):** `file_type#file_date` (e.g. `positions#20250128`). Groups all files of same type and date; good for “list all files for this date.” **Sort key (SK):** `s3_key` or full `s3_uri`. Unique per file. **Idempotency key (logical):** `source_system|file_type|file_date|file_name`. Stored as attribute or derived from PK+SK. Before starting job we **get** by PK+SK. If item exists and `status = SUCCESS`, skip.

---

### Q2.2 What attributes do you store in the audit row?

**Answer:**

| Attribute | Purpose |
|-----------|---------|
| `s3_uri`, `file_type`, `file_date` | Lineage; key. |
| `record_count`, `reject_count` | Data quality. |
| `status` | PENDING \| SUCCESS \| PARTIAL \| FAILED. |
| `processed_at`, `job_id` | Traceability. |
| `checksum` (optional) | File MD5/etag for extra idempotency. |

---

## 3. Technical Depth (Pain Points)

### Q3.1 How do you “reserve the run” so only one worker starts the job?

**Answer:** Orchestrator does `PutItem` with `condition_expression = attribute_not_exists(pk)` (or `attribute_not_exists(sk)` depending on key design). One worker wins; the other gets ConditionalCheckFailed, then GetItem and skips if PENDING or SUCCESS.

---

### Q3.2 How do you update on completion without overwriting SUCCESS?

**Answer:** Glue or completion Lambda does `UpdateItem` with `condition_expression = status = :pending` so we only transition PENDING → SUCCESS/FAILED. Never overwrite SUCCESS.

---

### Q3.3 Do you use strongly consistent or eventually consistent read for the idempotency check?

**Answer:** Use **strongly consistent read** on GetItem for idempotency check (default is eventually consistent). We need to see the latest state before deciding to start the job.

---

### Q3.4 How do you size capacity (on-demand vs provisioned)? What if you get throttled?

**Answer:** For low volume (one row per file per day) **on-demand** is simple. For high volume use provisioned; size RCU/WCU from reads per SQS message and writes per file. Throttling: back off and retry in Lambda; alarm on ThrottledRequests.

---

### Q3.5 How does compliance use the audit table?

**Answer:** Compliance can query “what files did we process, when, outcome?” Optional: TTL to archive; or keep forever for regulatory lineage.

---

## 4. Scenario & Design

### Q4.1 What if two messages for the same file arrive at once?

**Answer:** Conditional put: only one PutItem (status PENDING) succeeds. The other gets ConditionalCheckFailed; GetItem sees PENDING or SUCCESS and skips. Only one job runs.

---

### Q4.2 How do you handle replay (same file re-dropped with fix)?

**Answer:** By default we skip if SUCCESS. For replay we have a controlled process: ops can clear the audit row or use a separate “reprocess” path that overwrites by file_date.

---

### Q4.3 Can you query “all failed files for last 7 days”?

**Answer:** Query each date by PK or use a **GSI** with status (or sparse GSI where status = FAILED). Or export to S3/Athena for analytics.

---

## 5. Data Engineering Metrics (DynamoDB)

### Q5.1 What DynamoDB-related metrics do you track?

**Answer:**

| Metric | What to track | Why |
|--------|----------------|-----|
| **Item count by file_date** | Query by PK | Files processed vs expected. |
| **Status distribution** | SUCCESS vs FAILED vs PARTIAL | Failure rate; data quality. |
| **Throttled requests** | CloudWatch | Need more capacity or backoff. |

---

## 6. Evaluation Cheat Sheet

| Area | What good looks like | Red flag |
|------|----------------------|----------|
| **Key design** | PK+SK; idempotency key; query by file_date | Single key; can’t list by date |
| **Conditional writes** | PutItem only if not exists; UpdateItem only if PENDING | No conditions; overwrite SUCCESS |
| **Consistency** | Strong read for idempotency check | Eventually consistent read for check |
| **Compliance** | Audit attributes; lineage; retention | No audit or “we just log” |

Use this when they ask “how do you prevent duplicate processing?” or “where do you store audit?”
