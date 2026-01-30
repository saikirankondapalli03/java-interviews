# Component: DynamoDB (Audit & Idempotency)

**Role in flow:** Store one row per file with idempotency key. Orchestrator checks before starting the job; Glue (or completion Lambda) updates on success/failure. Used for skip-on-replay and compliance/lineage. **Implementation:** DynamoDB or PostgreSQL (RDS) both work; this doc uses DynamoDB (serverless, no connection pool in Lambda). Same logic applies to Postgres: unique constraint on idempotency key + conditional insert/update.

---

## 1. Interview Pain Points & Nuances

### Key Design

- **Partition key (PK):** `file_type#file_date` (e.g. `positions#20250128`). Groups all files of same type and date; good for "list all files for this date."
- **Sort key (SK):** `s3_key` or full `s3_uri`. Unique per file.
- **Idempotency key (logical):** `source_system|file_type|file_date|file_name`. Stored as attribute or derived from PK+SK. Before starting job we **get** by PK+SK. If item exists and `status = SUCCESS`, skip.

### Attributes

| Attribute | Purpose |
|-----------|---------|
| `s3_uri`, `file_type`, `file_date` | Lineage; key. |
| `record_count`, `reject_count` | Data quality. |
| `status` | PENDING \| SUCCESS \| PARTIAL \| FAILED. |
| `processed_at`, `job_id` | Traceability. |
| `checksum` (optional) | File MD5/etag for extra idempotency. |

### Conditional Writes

- **Reserve the run:** Orchestrator does `PutItem` with `condition_expression = attribute_not_exists(pk)`. One worker wins; the other gets ConditionalCheckFailed, then GetItem and skips if PENDING or SUCCESS.
- **Update on completion:** Glue or completion Lambda does `UpdateItem` with `condition_expression = status = :pending` so we only transition PENDING → SUCCESS/FAILED. Never overwrite SUCCESS.

### Consistency and Capacity

- **Read:** Use **strongly consistent read** on GetItem for idempotency check (default is eventually consistent).
- **Capacity:** For low volume (one row per file per day), **on-demand** is simple. For high volume, provisioned; size RCU/WCU from reads per SQS message and writes per file. Throttling: back off and retry in Lambda.

### Compliance

- **Audit:** Compliance can query "what files did we process, when, outcome?" Optional: TTL to archive; or keep forever for regulatory lineage.

---

## 2. Data Engineering Metrics (DynamoDB)

| Metric | What to track | Why |
|--------|----------------|-----|
| **Item count by file_date** | Query by PK | Files processed vs expected. |
| **Status distribution** | SUCCESS vs FAILED vs PARTIAL | Failure rate; data quality. |
| **Throttled requests** | CloudWatch | Need more capacity or backoff. |

---

## 3. Likely Interview Questions & Answers

- **Why DynamoDB and not PostgreSQL (or RDS)?** For *idempotency and audit only*, **PostgreSQL is perfectly fine**. You need: (1) a unique constraint on the idempotency key, (2) a conditional “insert only if not exists” (or “update only if status = PENDING”) so only one worker wins, (3) strong consistency on the read-before-start. PostgreSQL gives you that with a simple table + unique index + `SELECT ... FOR UPDATE` or `INSERT ... ON CONFLICT DO NOTHING` / conditional update. **Why we show DynamoDB:** serverless, no connection pool in Lambda (each invocation gets a new HTTP call to DynamoDB), auto-scaling, and same AWS-native story as S3/SQS/Glue. If your org already runs PostgreSQL (e.g. for the API or reporting) and you want one less moving part, **using the same RDS for the audit table is a valid and common choice.** Interview: *“We use DynamoDB for audit and idempotency so Lambda doesn’t need a DB connection pool and we get serverless scaling. You could do the same with PostgreSQL: one table, unique constraint on the idempotency key, conditional insert/update so only one job runs per file.”*
- **What if two messages for the same file arrive at once?** Conditional put: only one PutItem (status PENDING) succeeds. The other gets ConditionalCheckFailed; GetItem sees PENDING or SUCCESS and skips. Only one job runs.
- **How do you handle replay (same file re-dropped with fix)?** By default we skip if SUCCESS. For replay we have a controlled process: ops can clear the audit row or use a separate "reprocess" path that overwrites by file_date.
- **Can you query "all failed files for last 7 days"?** Query each date by PK or use a **GSI** with status (or sparse GSI where status = FAILED). Or export to S3/Athena for analytics.

Use this when they ask "how do you prevent duplicate processing?" or "where do you store audit?"
