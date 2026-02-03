# 04 – Audit & Idempotency (DynamoDB)

In production: DynamoDB table (or RDS) with PK = `file_type#file_date`, SK = `s3_key`. One row per file; conditional writes for reserve and completion.

## Key design

- **PK**: `file_type#file_date` (e.g. positions#20250201)
- **SK**: `s3_key` or full s3_uri
- **Idempotency key (logical)**: `source_system|file_type|file_date|file_name`
- **Reserve**: PutItem with condition `attribute_not_exists(pk)` (or equivalent)
- **Complete**: UpdateItem with condition `status = :pending`

## Code

- **AuditRecord** – attributes: s3_uri, file_type, file_date, status, record_count, reject_count, processed_at, job_id.
- **AuditStore** – interface: reserveRun, getByKey, completeRun.
- **InMemoryAuditStore** – in-memory implementation for local/demo.

Next step: **05-batch-processing** (Glue/PySpark equivalent: read CSV, validate, curated + quarantine, update audit).
