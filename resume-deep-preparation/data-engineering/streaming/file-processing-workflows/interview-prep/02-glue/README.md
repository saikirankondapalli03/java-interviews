# 02 – Glue (PySpark) batch job

AWS Glue job that the **orchestrator Lambda** (01-lambda) starts. It reads the CSV from S3, validates schema and business rules, writes valid rows to curated S3 (Parquet) and optionally to Kafka/MSK, invalid rows to quarantine, and updates the DynamoDB audit row.

## Pipeline position

```
S3 → SNS → SQS → Lambda (01-lambda) → Glue (this job)
                                         ↓
                              curated S3 (Parquet) + optional MSK
                              quarantine S3 (rejects)
                              DynamoDB audit update
```

## Flow

1. **Read CSV** from S3 at `--input-path` (and optional `--file-type`, `--file-date` from Lambda).
2. **Validate** schema (required columns, parseable types) and business rules:
   - **Positions:** no duplicate `loan_id`, `quantity` > 0, `loan_value_usd` >= 0, `settle_date` >= `trade_date`, non-empty key fields.
   - **MTM P&L:** no duplicate `(file_date, book_id, desk_id, loan_id)`, numerics parseable, required fields non-empty.
3. **Valid rows** → curated S3 (Parquet, partition `file_date=YYYYMMDD`); optionally to Kafka/MSK (see below).
4. **Invalid rows** → quarantine S3 (Parquet with `reject_reason` column).
5. **Update DynamoDB** audit: `status` (SUCCESS | PARTIAL | FAILED), `record_count`, `reject_count`, `processed_at`. Condition: only update if `status = PENDING` (set by Lambda).

## Job arguments (from Lambda)

| Argument        | Required | Description                                      |
|----------------|----------|--------------------------------------------------|
| `--input-path` | Yes      | S3 URI of the CSV (e.g. `s3://bucket/positions/equilend_positions_20250201.csv`). |
| `--file-type`  | Yes      | `positions` or `mtm_pnl`.                        |
| `--file-date`  | Yes      | `YYYYMMDD` (e.g. `20250201`).                     |

Optional (set in Glue job **Arguments** or pass from Step Functions):

| Argument             | Default   | Description |
|----------------------|-----------|-------------|
| `--audit-table-name` | (empty)   | DynamoDB table for audit; if empty, skip update. |
| `--curated-bucket`   | from path | S3 bucket for curated Parquet. |
| `--quarantine-bucket`| same      | S3 bucket for quarantine. |
| `--source-system`    | EQUILEND  | Used to derive idempotency key (must match Lambda). |

## Files

| File          | Purpose |
|---------------|---------|
| `glue_etl.py` | Glue script: read, validate, write curated/quarantine, update DynamoDB. |
| `README.md`   | This file. |

## DynamoDB audit (same as Lambda)

- **PK:** `file_type#file_date` (e.g. `positions#20250201`).
- **SK:** idempotency key (e.g. `EQUILEND|positions|20250201|equilend_positions_20250201.csv`).

Glue derives the idempotency key from `--source-system`, `--file-type`, `--file-date`, and the filename in `--input-path`. The Lambda has already inserted a row with `status = PENDING` and (after start) `job_id = JobRunId`. Glue at the end updates:

- `status` → SUCCESS | PARTIAL | FAILED  
- `record_count`, `reject_count`, `processed_at`  
- Condition: `status = PENDING` (so a late duplicate run does not overwrite SUCCESS).

## S3 paths

- **Curated:** `s3://{curated_bucket}/equilend/{file_type}/file_date={file_date}/` (Parquet).
- **Quarantine:** `s3://{quarantine_bucket}/quarantine/equilend/{file_type}/file_date={file_date}/{filename}.parquet` (includes `reject_reason`).

## Optional: write to Kafka (MSK)

To publish valid rows to MSK, add in the Glue job:

1. **Job parameters / library:** include the Spark Kafka connector (e.g. `org.apache.spark:spark-sql-kafka-0-10_2.12:3.x` or Glue’s built-in support).
2. In `glue_etl.py`, after writing to curated S3, add a block that writes `valid_df` to Kafka, e.g.:

   ```python
   # Example (enable when KAFKA_BOOTSTRAP_SERVERS and TOPIC are set)
   # valid_df.selectExpr("to_json(struct(*)) AS value").write.format("kafka") \
   #   .option("kafka.bootstrap.servers", os.environ.get("KAFKA_BOOTSTRAP_SERVERS")) \
   #   .option("topic", os.environ.get("TOPIC", "equilend.positions.curated")).save()
   ```

3. Set `KAFKA_BOOTSTRAP_SERVERS` and `TOPIC` in the Glue job configuration (or pass as job arguments and read in the script).

See component doc `05-GLUE-PYSPARK-BATCH.md` for idempotent producer and topic naming.

## IAM (Glue job role)

- **S3:** Read landing bucket (input path); write curated and quarantine buckets (or same bucket, different prefixes).
- **DynamoDB:** `dynamodb:UpdateItem` on the audit table (condition `status = PENDING`).
- **CloudWatch Logs:** Standard Glue logging.

## Glue Bookmarks

The script uses `create_dynamic_frame.from_options` with `transformation_ctx="equilend_source"` so Glue can track which S3 paths have been processed. **Enable "Job bookmark"** in the Glue job definition (Console → Job details → Job bookmark: Enable). On retry with the same `--input-path`, Glue skips already-processed files and returns empty data—no duplicate writes, idempotent retries.

## Deployment (Glue console / IaC)

1. Create a Glue job (Spark script job), Python 3, upload or reference `glue_etl.py`.
2. **Enable Job bookmark** in the job definition (required for bookmark-based idempotency).
3. Set **Job parameters** (arguments) so that the Lambda only needs to pass `--input-path`, `--file-type`, `--file-date`; add `--audit-table-name` (and optionally `--curated-bucket`, `--quarantine-bucket`) if needed.
4. Ensure the job role has S3 and DynamoDB permissions as above.
5. Lambda calls `glue.start_job_run(JobName=..., Arguments={"--input-path": s3_uri, "--file-type": file_type, "--file-date": file_date})`.

## Interview talking points

| Topic | What this sample does |
|-------|------------------------|
| **Job args** | Lambda passes `--input-path`, `--file-type`, `--file-date`; script uses `getResolvedOptions` for required and parses argv for optional. |
| **Validation** | Schema (required + parseable) and business rules (no duplicate key, quantity > 0, settle >= trade, etc.); valid vs invalid split; invalid get `reject_reason`. |
| **Partial success** | Valid → curated; invalid → quarantine; audit has `record_count` and `reject_count`; status PARTIAL when both > 0. |
| **Audit update** | At end, `UpdateItem` with condition `status = PENDING` so we never overwrite SUCCESS. |
| **Glue bookmarks** | DynamicFrame with `transformation_ctx`; enable Job bookmark in Glue. Retries skip already-processed files. |
| **Curated write** | Parquet, partition by `file_date`; production would use staging prefix then move (see component doc). |
| **Idempotency key** | Derived from source_system, file_type, file_date, filename so it matches Lambda’s DynamoDB SK. |

## References

- `../../components/05-GLUE-PYSPARK-BATCH.md` – Glue nuances, validation, writes, DynamoDB, Kafka.
- `../../components/04-DYNAMODB-AUDIT-IDEMPOTENCY.md` – Audit table design and conditionals.
- `../../INTERVIEW-PREP-EQUILEND-FILE-WORKFLOW.md` – Part B: file specs (positions, mtm_pnl).
- `../01-lambda/README.md` – Orchestrator that triggers this job.
- `../glue-interview-prep` – Glue concepts and design reference.
