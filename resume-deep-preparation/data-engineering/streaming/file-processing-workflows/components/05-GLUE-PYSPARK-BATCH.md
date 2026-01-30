# Component: AWS Glue + PySpark (Batch File Processing)

**Role in flow:** Glue job runs PySpark that reads CSV from S3 landing, validates schema and business rules, writes valid rows to curated S3 (Parquet) and to MSK (Kafka); writes invalid rows to quarantine; updates DynamoDB audit.

---

## 1. Interview Pain Points & Nuances

### Glue vs EMR (When to Say What)

| Aspect | Glue | EMR |
|--------|------|-----|
| **Managed** | Fully managed; no cluster to maintain. | You manage (or use managed scaling); more control. |
| **Cost** | Pay per DPU-minute; job-based. | Pay per instance-hour; cluster can be long-lived or transient. |
| **Use when** | File-based, scheduled or event-triggered; standard Spark/PySpark. | Need custom Spark config, specific runtime, or tight cost control at scale. |
| **Interview** | "We use Glue for the Equilend file job: event-triggered PySpark, read CSV from S3, validate, write Parquet and Kafka. No cluster to manage; pay per run." | "We could use EMR for the same job if we needed custom Spark versions or long-running clusters; for per-file batch we chose Glue for simplicity." |

### Job Bookmarks (Glue) — Important Nuance

- **What it is:** Glue can track "which S3 objects have been processed" in a bookmark state. Next run only processes **new** objects. Designed for **continuous** ingestion (many files over time).
- **Our pattern:** We process **one file per job run** (triggered by one SQS message). We pass `--input-path s3://bucket/key` as a job argument. We **do not** rely on Glue job bookmarks for "which file to read" — we explicitly pass the path. So **job bookmarks are optional** here; we use them only if we want Glue to skip re-reading the same path in a re-run (usually we want idempotency at the **orchestrator** level: don't start the job twice; if we do start twice, Glue reads same file and we overwrite by file_date). **Interview:** "We pass the input path as a job argument; we don't use Glue job bookmarks for file selection because we're one-file-per-run and idempotency is handled by the orchestrator and DynamoDB."

### DPUs (Data Processing Units)

- **What:** 1 DPU = 4 vCPU + 16 GB RAM. Glue charges per DPU-minute. You set **number of workers** (each worker = 1 DPU by default) and **worker type** (G.1X = 1 DPU, G.2X = 2 DPU). More workers = more parallelism for reads/writes.
- **Sizing:** For 10K–2M rows, start with 2–10 workers (G.1X). Tune by job duration and cost. **Interview:** "We sized Glue workers based on typical file size; we use G.1X workers and scale worker count so the job finishes within our SLA window. We monitor DPU-minutes per run for cost."

### Getting Job Arguments in Glue (PySpark)

- **From orchestrator:** Lambda/Step Functions pass `--input-path`, `--file-type`, `--file-date` (and optionally `--idempotency-key`) in `Arguments` of `start_job_run`.
- **In script:** Use `glueContext.getResolvedOptions(sys.argv, ['JOB_NAME', 'input_path', 'file_type', 'file_date'])` to read them. Or parse `sys.argv` manually. **Interview:** "We pass input-path, file-type, and file-date as job arguments; in the Glue script we use getResolvedOptions to read them and then read only that S3 path."

### Read CSV from S3

- **Spark:** `spark.read.option("header", True).option("delimiter", ",").csv(s3_path)`. Handle schema: infer or provide explicit schema (recommended for validation).
- **Large files:** Spark partitions the read by default (splits by blocks). No need to "chunk" manually.
- **Schema:** Define schema (StructType) so that invalid types fail fast and we get consistent column types. Optional columns: use nullable or default.

### Validation (Critical for Interview)

- **Schema validation:** Required columns present; types correct (date parseable, decimal parseable). Rows that fail go to **quarantine** (we don't fail the whole file).
- **Business rules:** No duplicate `loan_id` (positions); `quantity` > 0; `settle_date` >= `trade_date`; non-empty key fields. Implement with Spark DataFrame filters or UDFs; split into valid vs invalid DataFrames.
- **Partial failure:** Valid rows → write to curated S3 + Kafka; invalid rows → write to quarantine with reject reason (e.g. column name + rule). Audit: `record_count` (valid), `reject_count` (invalid).
- **Interview:** "We validate schema and business rules in Spark. Valid rows go to curated S3 and Kafka; invalid rows go to S3 quarantine with a reject reason. We don't fail the whole file — we get record_count and reject_count in the audit table so ops can see partial success."

### Write to Curated S3 (Parquet)

- **Partitioning:** Write to `s3://curated-bucket/equilend/positions/file_date=YYYYMMDD/` (partition by file_date). Enables partition pruning in Athena and Spark later.
- **Staging then commit:** Write to a staging prefix (e.g. `_staging/`) then rename/move to final partition so downstream (Athena, Snowflake COPY) never sees partial data. Or use Spark's "write then rename partition" pattern. **Interview:** "We write to a staging prefix first, then move to the final partition so downstream only sees complete partitions."
- **Format:** Parquet; compression (e.g. snappy) for size and speed.

### Write to Kafka (MSK)

- **Spark Kafka connector:** `df.write.format("kafka").option("kafka.bootstrap.servers", ...).option("topic", "equilend.positions.curated").save()`. Serialize DataFrame rows to bytes (e.g. JSON or Avro). Need schema registry or agreed format for consumers.
- **Per-record or micro-batch:** We write the whole DataFrame as a batch to Kafka. Kafka sees many messages (one per row) or we can coalesce to fewer larger messages depending on consumer expectations.
- **Idempotent producer:** Kafka producer can be configured for idempotence (exactly-once semantics) to avoid duplicate records on retry. **Interview:** "We use the Spark Kafka sink to publish curated rows to MSK. We configure the producer for idempotence where we need exactly-once; our consumer (Spark Streaming) can then do exactly-once or at-least-once with overwrite by file_date in Snowflake."

### Update DynamoDB (Audit)

- **At end of job:** Use boto3 (in Glue, Python) to call `dynamodb.update_item` with status SUCCESS or FAILED (or PARTIAL if we have rejects), record_count, reject_count, processed_at, job_run_id. Partition key + sort key = idempotency key so we update the right row.
- **Condition:** Update only if status is PENDING (so we don't overwrite SUCCESS with a late duplicate run). **Interview:** "The Glue job at the end updates DynamoDB with status, record_count, reject_count, and processed_at. We use a condition so we only update if status was PENDING."

### Glue Catalog vs Our Schema

- **Glue Data Catalog:** Can hold table definitions for S3 data (used by Athena, Spark). For our **landing** CSV we might not register a table (we read by path). For **curated** Parquet we can create a table so Athena can query. **Interview:** "We use the Glue catalog for curated Parquet tables so Athena and downstream jobs can query by partition. Landing is read by path from job arguments."

### Failure Handling

- **Job fails mid-run:** Glue marks the run as FAILED. We may not have updated DynamoDB yet — so audit row stays PENDING. Orchestrator (Step Functions or completion Lambda) can set status to FAILED when it sees Glue run state FAILED. Or Glue script can wrap in try/except and update DynamoDB to FAILED before re-raising.
- **Partial write:** We write curated and quarantine in a deterministic order (e.g. write quarantine first, then curated, then DynamoDB). If we crash after curated but before DynamoDB, next run might reprocess the file (idempotency check sees PENDING or no row) and overwrite — acceptable if we overwrite by file_date.

---

## 2. Data Engineering Metrics (Glue)

| Metric | What to track | Why |
|--------|----------------|-----|
| **Job duration** | Time per run | SLA; right-size DPUs. |
| **DPU-minutes** | Cost per run | Cost control. |
| **Records read / written** | From Spark or Glue metrics | Throughput; validate record_count. |
| **Reject count** | From audit table | Data quality; quarantine growth. |
| **Job success rate** | SUCCESS vs FAILED runs | Reliability. |

---

## 3. Likely Interview Questions & Answers

- **Why Glue and not Lambda for the file?** Lambda has 15 min max, 10 GB RAM. Our files can be millions of rows; we need distributed processing (Spark). Glue gives managed Spark; we pay per run.
- **Do you use Glue job bookmarks?** We pass the input path as a job argument (one file per run). We don't use job bookmarks for file selection; idempotency is in the orchestrator and DynamoDB. We could use bookmarks if we had a single job processing a folder of new files.
- **How do you handle bad rows?** We validate in Spark; valid rows go to curated S3 and Kafka; invalid rows go to S3 quarantine with reject reason. Audit has record_count and reject_count. We don't fail the whole file.
- **How do you avoid duplicate writes to Kafka/Snowflake if the job runs twice?** Idempotency at orchestrator (don't start job twice). If job does run twice, we overwrite by file_date in Snowflake and/or use idempotent Kafka producer; consumer (Spark Streaming) overwrites same partition so end state is correct.
- **Where is the schema defined?** In config (S3 or Parameter Store): column list, types, mandatory flags, validation rules. Glue/Spark reads config at runtime so we can add optional columns without code deploy.

Use this when they drill into "how does the Glue job work?" or "how do you validate and write to S3 and Kafka?"
