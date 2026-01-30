# Component: Observability (CloudWatch, Logs, Metrics, Alarms)

**Role in flow:** Logs, metrics, and alarms for every stage of the pipeline so we can see what's running, what failed, and why. Supports SLA monitoring, debugging, and compliance lineage.

---

## 1. Interview Pain Points & Nuances

### What Interviewers Care About

- **Can you see when a file was received and when it was fully processed?** Yes: S3 event time (in SQS message), Glue job start/end, DynamoDB audit (processed_at, status). End-to-end freshness = processed_at − file_drop_time.
- **Can you see why a job failed?** Yes: CloudWatch Logs for Glue driver and executor logs; DynamoDB audit (status FAILED, optional error_message); SQS DLQ message body (bucket, key).
- **Can you alert when something is wrong?** Yes: CloudWatch Alarms on DLQ depth, Glue job failure, DynamoDB FAILED count, SQS queue depth, consumer lag (MSK), sync job failure.
- **Can compliance trace lineage?** Yes: DynamoDB audit (file_name, s3_uri, file_date, record_count, reject_count, processed_at, job_id); optional S3 manifest or Redshift audit table.

### Logs

| Component | Where logs go | What to log |
|-----------|----------------|-------------|
| **S3** | No application logs | Event delivery → SNS/SQS. |
| **Lambda / Step Functions** | CloudWatch Logs (auto) | Invocation, DynamoDB check result, Glue JobRunId, errors. |
| **Glue** | CloudWatch Logs (driver + executors) | Job start/end, input path, record_count, reject_count, DynamoDB update, exceptions. |
| **Spark Streaming** | CloudWatch Logs (if on EMR) or Glue | Batch start/end, offsets, Redshift write result. |
| **Application (Glue script)** | CloudWatch via Glue | File received, validation pass/fail, rows valid/rejected, job duration. |

**Interview:** "We use CloudWatch Logs for Lambda, Step Functions, and Glue. Glue driver logs show file path, record counts, and any exceptions. We don't log PII (e.g. borrower_name) in plain text; we mask or omit."

### Metrics (Custom and Built-in)

| Metric | Source | Use |
|--------|--------|-----|
| **EquilendFilesProcessed** | Custom (Glue or Lambda) | Count of files processed per day; compare to expected (e.g. 2). |
| **EquilendRowsValid / EquilendRowsRejected** | Custom (Glue) | Data quality; reject rate. |
| **ProcessingLatency** | Custom (Glue or Lambda) | Time from file event to processed_at; SLA. |
| **SQS: ApproximateNumberOfMessagesVisible** | Built-in | Queue depth; backpressure. |
| **SQS DLQ: same** | Built-in | **Alert if > 0.** |
| **Glue: JobRun succeeded / failed** | Built-in | Job success rate. |
| **Glue: JobRun duration** | Built-in | SLA; right-size DPUs. |
| **MSK: Consumer lag** | Built-in or Kafka metrics | Pipeline keeping up; scale consumers. |
| **DynamoDB: ThrottledRequests** | Built-in | Capacity; backoff. |
| **Sync job: success / duration** | Glue built-in or custom | RDBMS freshness; sync SLA. |

**Interview:** "We emit custom metrics from the Glue job (files processed, rows valid/rejected, processing latency) and use built-in metrics for SQS depth, DLQ depth, Glue job success/duration, and Kafka consumer lag. We alarm on DLQ depth, job failure, and consumer lag."

### Alarms (Critical for Interview)

| Alarm | Condition | Action |
|-------|-----------|--------|
| **DLQ depth > 0** | SQS ApproximateNumberOfMessagesVisible (DLQ) > 0 | SNS topic → email/Slack; ops must inspect and re-drive or fix. |
| **Glue job failed** | Glue JobRun state = FAILED | SNS topic; ops check logs and DynamoDB audit. |
| **Consumer lag > threshold** | MSK consumer lag > N messages or M minutes | SNS topic; scale consumers or optimize sink. |
| **Sync job failed** | Glue sync job state = FAILED | SNS topic; RDBMS stale; retry. |
| **Expected file missing** | Custom: "files processed today" < expected (e.g. 2) | SNS topic; file not received or job not run. |

**Interview:** "We have alarms on DLQ depth (any message in DLQ), Glue job failure, Kafka consumer lag, and sync job failure. We also have a check for expected file count per day — if we didn't process the expected number of files by T+1 morning we alert."

### Data Pipeline Metrics (Data Engineering Lens)

- **Freshness:** Time from file drop (S3 event_time) to data available in Redshift (and RDBMS). Target: T+1 morning (e.g. 6 AM). Track: processed_at − event_time per file; alert if > SLA.
- **Completeness:** Expected files per day (e.g. 2: positions + P&L) vs files processed (DynamoDB count by file_date). Alert if missing.
- **Correctness:** record_count, reject_count in audit; quarantine object count. Alert if reject rate spikes.
- **Idempotency:** No duplicate rows in Redshift for same file_date/file_type. Implicit if we overwrite; no separate metric unless we detect duplicates (e.g. row count > expected).
- **Availability:** Pipeline runs when files arrive; DLQ and job failure alarms; retry and re-drive process.

**Interview:** "We track freshness (file drop to processed_at), completeness (expected vs actual files per day), and correctness (reject count, quarantine). We alarm on DLQ, job failure, and consumer lag for availability."

### X-Ray (Optional)

- **Tracing:** Lambda → Glue → MSK (and Step Functions). Optional for debugging slow or failed runs. **Interview:** "We could use X-Ray to trace across Lambda, Step Functions, and Glue for debugging; for this pipeline we rely on CloudWatch Logs and the audit table for traceability."

---

## 2. Likely Interview Questions & Answers

- **How do you know if a file was processed successfully?** DynamoDB audit: status = SUCCESS, record_count, reject_count, processed_at. We can also query Glue job runs by job name and time window.
- **How do you alert when something fails?** CloudWatch Alarms on SQS DLQ depth, Glue job failure, Kafka consumer lag, sync job failure. Alarms publish to SNS topic; we subscribe email/Slack/PagerDuty.
- **What metrics do you look at for data quality?** record_count and reject_count per file (audit table); quarantine object count; custom metric EquilendRowsRejected. We alarm if reject rate spikes or quarantine grows.
- **How do you monitor end-to-end latency?** Custom metric: ProcessingLatency = processed_at − event_time (from SQS message). We alarm if latency exceeds SLA (e.g. > 12 hours for T+1 morning).
- **How do you handle "file not received"?** We have an expected file count per day (e.g. 2). We can run a scheduled check (Lambda or Glue) that queries DynamoDB for files processed today and compares to expected; if missing, alert. Or we use CloudWatch metric "files processed" and alarm if below threshold by a cutoff time.

Use this when they ask "how do you monitor the pipeline?" or "how do you debug failures?" or "what metrics do you track?"
