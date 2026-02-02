# Component 05: Glue + PySpark (Batch) — Question & Answer Bank

**Reference:** `components/05-GLUE-PYSPARK-BATCH.md`  
**Interview coverage:** Intro, architecture, technical depth, scenarios, follow-ups, evaluation.

---

## 1. Intro & Context (Opening)

### Q1.1 What is the role of the Glue job in the file processing flow?

**Answer:** Glue job runs PySpark that reads CSV from S3 landing, validates schema and business rules, writes valid rows to curated S3 (Parquet) and to MSK (Kafka); writes invalid rows to quarantine; updates DynamoDB audit.

---

### Q1.2 Why Glue and not Lambda for the file?

**Answer:** Lambda has 15 min max, 10 GB RAM. Our files can be millions of rows; we need distributed processing (Spark). Glue gives managed Spark; we pay per run.

---

## 2. Architecture & Ownership

### Q2.1 Glue vs EMR—when do you use which?

**Answer:**

| Aspect | Glue | EMR |
|--------|------|-----|
| **Managed** | Fully managed; no cluster to maintain. | You manage (or use managed scaling); more control. |
| **Cost** | Pay per DPU-minute; job-based. | Pay per instance-hour; cluster can be long-lived or transient. |
| **Use when** | File-based, scheduled or event-triggered; standard Spark/PySpark. | Need custom Spark config, specific runtime, or tight cost control at scale. |
| **Interview** | “We use Glue for the Equilend file job: event-triggered PySpark, read CSV from S3, validate, write Parquet and Kafka. No cluster to manage; pay per run.” | “We could use EMR for the same job if we needed custom Spark versions or long-running clusters; for per-file batch we chose Glue for simplicity.” |

---

### Q2.2 Do you use Glue job bookmarks? Why or why not?

**Answer:** **What it is:** Glue can track “which S3 objects have been processed” in a bookmark state. Next run only processes **new** objects. Designed for **continuous** ingestion (many files over time). **Our pattern:** We process **one file per job run** (triggered by one SQS message). We pass `--input-path s3://bucket/key` as a job argument. We **do not** rely on Glue job bookmarks for “which file to read”—we explicitly pass the path. Job bookmarks are optional here; we use them only if we want Glue to skip re-reading the same path in a re-run. Idempotency is at the **orchestrator** level. **Interview:** “We pass the input path as a job argument; we don’t use Glue job bookmarks for file selection because we’re one-file-per-run and idempotency is handled by the orchestrator and DynamoDB.”

---

## 3. Technical Depth (Pain Points)

### Q3.1 What are DPUs and how do you size Glue workers?

**Answer:** 1 DPU = 4 vCPU + 16 GB RAM. Glue charges per DPU-minute. You set **number of workers** (each worker = 1 DPU by default) and **worker type** (G.1X = 1 DPU, G.2X = 2 DPU). More workers = more parallelism. For 10K–2M rows start with 2–10 workers (G.1X). Tune by job duration and cost. **Interview:** “We sized Glue workers based on typical file size; we use G.1X workers and scale worker count so the job finishes within our SLA window. We monitor DPU-minutes per run for cost.”

---

### Q3.2 How do you get job arguments (input-path, file-type, file-date) in the Glue script?

**Answer:** Lambda/Step Functions pass `--input-path`, `--file-type`, `--file-date` (and optionally `--idempotency-key`) in `Arguments` of `start_job_run`. In script: `glueContext.getResolvedOptions(sys.argv, ['JOB_NAME', 'input_path', 'file_type', 'file_date'])` to read them. Or parse `sys.argv` manually. **Interview:** “We pass input-path, file-type, and file-date as job arguments; in the Glue script we use getResolvedOptions to read them and then read only that S3 path.”

---

### Q3.3 How do you validate data and handle bad rows?

**Answer:** **Schema validation:** Required columns present; types correct (date parseable, decimal parseable). Rows that fail go to **quarantine**. **Business rules:** No duplicate `loan_id` (positions); `quantity` > 0; `settle_date` >= `trade_date`; non-empty key fields. Implement with Spark DataFrame filters or UDFs; split into valid vs invalid DataFrames. **Partial failure:** Valid rows → write to curated S3 + Kafka; invalid rows → write to quarantine with reject reason. Audit: `record_count` (valid), `reject_count` (invalid). **Interview:** “We validate schema and business rules in Spark. Valid rows go to curated S3 and Kafka; invalid rows go to S3 quarantine with a reject reason. We don’t fail the whole file—we get record_count and reject_count in the audit table.”

---

### Q3.4 How do you write to curated S3 (Parquet) without exposing partial data?

**Answer:** Write to a **staging prefix** (e.g. `_staging/`) then rename/move to final partition so downstream (Athena, Snowflake COPY) never sees partial data. Or use Spark’s “write then rename partition” pattern. Format: Parquet; compression (e.g. snappy). **Interview:** “We write to a staging prefix first, then move to the final partition so downstream only sees complete partitions.”

---

### Q3.5 How do you write to Kafka (MSK) from Glue? Idempotent producer?

**Answer:** Spark Kafka connector: `df.write.format("kafka").option("kafka.bootstrap.servers", ...).option("topic", "equilend.positions.curated").save()`. Serialize rows (e.g. JSON or Avro). **Idempotent producer:** Kafka producer can be configured for idempotence (exactly-once semantics) to avoid duplicate records on retry. **Interview:** “We use the Spark Kafka sink to publish curated rows to MSK. We configure the producer for idempotence where we need exactly-once; our consumer overwrites by file_date in Snowflake.”

---

### Q3.6 How does the Glue job update DynamoDB (audit) at the end?

**Answer:** Use boto3 (in Glue, Python) to call `dynamodb.update_item` with status SUCCESS or FAILED (or PARTIAL if we have rejects), record_count, reject_count, processed_at, job_run_id. Partition key + sort key = idempotency key. **Condition:** Update only if status is PENDING so we don’t overwrite SUCCESS with a late duplicate run.

---

### Q3.7 What if the job fails mid-run? Partial write?

**Answer:** Glue marks the run as FAILED. We may not have updated DynamoDB yet—audit row stays PENDING. Orchestrator (Step Functions or completion Lambda) can set status to FAILED when it sees Glue run state FAILED. Or Glue script can wrap in try/except and update DynamoDB to FAILED before re-raising. **Partial write:** Write quarantine first, then curated, then DynamoDB. If we crash after curated but before DynamoDB, next run might reprocess (idempotency sees PENDING or no row) and overwrite—acceptable if we overwrite by file_date.

---

## 4. Scenario & Design

### Q4.1 How do you avoid duplicate writes to Kafka/Snowflake if the job runs twice?

**Answer:** Idempotency at orchestrator (don’t start job twice). If job does run twice we overwrite by file_date in Snowflake and/or use idempotent Kafka producer; consumer (Spark Streaming) overwrites same partition so end state is correct.

---

### Q4.2 Where is the schema defined?

**Answer:** In config (S3 or Parameter Store): column list, types, mandatory flags, validation rules. Glue/Spark reads config at runtime so we can add optional columns without code deploy.

---

### Q4.3 Do you use Glue Data Catalog for landing and curated?

**Answer:** For **landing** CSV we might not register a table (we read by path). For **curated** Parquet we can create a table so Athena can query. **Interview:** “We use the Glue catalog for curated Parquet tables so Athena and downstream jobs can query by partition. Landing is read by path from job arguments.”

---

## 5. Data Engineering Metrics (Glue)

### Q5.1 What Glue/Spark metrics do you track?

**Answer:**

| Metric | What to track | Why |
|--------|----------------|-----|
| **Job duration** | Time per run | SLA; right-size DPUs. |
| **DPU-minutes** | Cost per run | Cost control. |
| **Records read / written** | From Spark or Glue metrics | Throughput; validate record_count. |
| **Reject count** | From audit table | Data quality; quarantine growth. |
| **Job success rate** | SUCCESS vs FAILED runs | Reliability. |

---

## 6. Evaluation Cheat Sheet

| Area | What good looks like | Red flag |
|------|----------------------|----------|
| **Bookmarks** | Explicit path from args; idempotency at orchestrator | “We use bookmarks for file selection” (we’re one-file-per-run) |
| **Validation** | Schema + business rules; quarantine + reject reason | Fail whole file or no quarantine |
| **Write strategy** | Staging then commit; Parquet; overwrite by file_date | Expose partial partitions |
| **DynamoDB update** | Condition: only if PENDING; record_count, reject_count | Overwrite SUCCESS or no condition |

Use this when they drill into “how does the Glue job work?” or “how do you validate and write to S3 and Kafka?”
