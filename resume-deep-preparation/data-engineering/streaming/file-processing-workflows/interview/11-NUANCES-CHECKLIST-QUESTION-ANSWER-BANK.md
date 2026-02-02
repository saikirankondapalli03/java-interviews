# Component 11: Nuances Checklist — Question & Answer Bank

**Reference:** `components/11-NUANCES-CHECKLIST.md`  
**Interview coverage:** Security, re-drive, DR, testing, runbooks, SLO. “What about …?” answers so you leave no stone unturned.

---

## 1. Security (Single Narrative)

### Q1.1 How do you handle encryption across the pipeline?

**Answer:** **S3:** SSE-S3 or SSE-KMS on landing, curated, quarantine. **MSK:** TLS in transit; KMS at rest. **Snowflake:** Encryption at rest + TLS. Glue/Lambda use VPC endpoints or private subnets where needed; no PII in logs (mask or omit). **Interview:** “We use SSE on S3, TLS and KMS for MSK, and Snowflake’s default encryption. We don’t log PII in plain text.”

---

### Q1.2 What IAM and least-privilege do you apply?

**Answer:** **Glue role:** S3 landing/curated/quarantine read/write, DynamoDB, MSK produce. **Spark Streaming role:** MSK consume, Snowflake/S3 write. **Lambda/Step Functions:** SQS, DynamoDB, Glue StartJobRun. MSK: IAM auth so no SASL passwords. **Interview:** “Each service has a role with least-privilege; Glue can’t read other buckets; Lambda can’t invoke arbitrary jobs.”

---

### Q1.3 Where do you store DB and Snowflake credentials?

**Answer:** Secrets Manager or Parameter Store; Glue/Lambda fetch at runtime. **Interview:** “We don’t put credentials in code; we use Secrets Manager for RDBMS and Snowflake and grant Glue/Lambda roles read access.”

---

## 2. DLQ Re-drive (Concrete Steps)

### Q2.1 When does a message land in the DLQ and what do you do?

**Answer:** **When:** CloudWatch alarm fires because DLQ depth > 0. **Steps:** (1) Inspect DLQ message (bucket, key, event_time). (2) Decide: fix file and re-drop to S3, or re-drive message. (3) Re-drive: Lambda (or script) receives from DLQ, (optionally fixes or validates), then sends same body to **main SQS queue** (or invokes Glue with `--input-path` from message). (4) Delete from DLQ after successful re-drive. (5) If it fails again it goes back to DLQ (max receives); investigate logs and DynamoDB audit. **Interview:** “When a message lands in the DLQ we’re alerted. Ops inspect the message, fix the file or path if needed, then either re-drop to S3 or re-send the message to the main queue. We can also trigger a manual Glue run with the path from the message for a one-off reprocess.”

---

## 3. Disaster Recovery (DR) / Backup

### Q3.1 What is your RPO/RTO and how do you achieve it?

**Answer:** Define targets (e.g. RPO 24h, RTO 4h). Pipeline is replayable from S3 (landing or curated); DynamoDB audit is metadata, not source of truth for data. **S3:** Versioning optional for landing/curated; cross-region replication if required for compliance. Lifecycle to Glacier for cost; keep landing long enough for replay (e.g. 7–30 days). **DynamoDB:** Point-in-time recovery (PITR) or on-demand backup for audit table. **Snowflake:** Time Travel, fail-safe; clone/restore as needed. **Replay:** Re-drop file to S3 (or copy from backup) and clear/update DynamoDB audit row for that file so the pipeline reprocesses. **Interview:** “We can replay from S3; our idempotency key and overwrite-by–file_date mean we don’t duplicate. We use S3 lifecycle and DynamoDB/warehouse backups for DR and compliance.”

---

## 4. Testing

### Q4.1 How do you test validation and the pipeline?

**Answer:** **Unit:** Validation rules and transforms (e.g. row-level checks) in Spark/Glue code; test with sample rows (valid/invalid). **Integration:** Trigger pipeline with test file in S3 (or mock S3/SQS with LocalStack); assert DynamoDB audit status, curated S3 output, and optionally Kafka/Snowflake. Step Functions + Glue in dev account. **Data quality:** record_count/reject_count in audit; quarantine count; optional Great Expectations or similar on curated Parquet. **Interview:** “We unit-test validation logic, run integration tests with test files in a dev environment, and monitor reject counts and quarantine for data quality.”

---

## 5. Dashboards & Runbooks

### Q5.1 What does your pipeline dashboard show?

**Answer:** One pane: SQS main + DLQ depth, Glue job success/duration, consumer lag, “files processed today” vs expected, last Snowflake load time. CloudWatch dashboard or Grafana with CloudWatch data source.

---

### Q5.2 What runbooks do you have for common failures?

**Answer:** (1) **DLQ > 0** → “DLQ runbook: inspect message, re-drive or fix file, clear DLQ.” (2) **Glue job failed** → “Check CloudWatch Logs and DynamoDB audit; fix input or code; re-run or re-drop file.” (3) **Consumer lag high** → “Scale consumers or optimize warehouse Snowflake write.” (4) **Expected file missing** → “Check S3 landing and SNS/SQS; alert Equilend or ops to re-drop.” **Interview:** “We have a CloudWatch dashboard for queue depth, job status, consumer lag, and file counts. Runbooks tell ops what to do when DLQ has messages, a job fails, or lag spikes.”

---

## 6. SLO / Error Budget (Optional)

### Q6.1 Do you define SLOs or error budgets?

**Answer:** **SLO example:** “99% of files processed within 12h of drop” or “T+1 6 AM data available for 99% of days.” **Error budget:** If we breach SLO (e.g. 2 days in a quarter) we spend “budget”; post-mortem and improve. **Interview:** “Our SLA is T+1 morning. We could define an SLO like 99% of files within 12h and track an error budget for prioritization.”

---

## 7. Quick “Did You Cover …?” Answers

| Topic | Covered? | Where to say it |
|-------|----------|------------------|
| Observability | Yes | Doc 10 + “metrics by stage” in 00. |
| Logs, metrics, alarms | Yes | 10: CloudWatch logs per component, custom + built-in metrics, alarms (DLQ, job failure, lag, sync, missing file). |
| Lineage / compliance | Yes | 00, 04, 08, 10: DynamoDB audit + optional Snowflake audit. |
| Security (encryption, IAM, secrets) | Partially (S3, MSK, Snowflake); narrative above | Use “Security (single narrative)” in this doc. |
| Re-drive from DLQ | Mentioned; steps here | Use “DLQ re-drive” above. |
| DR / backup / replay | Not explicit in component docs; summary here | Use “Disaster recovery” above. |
| Testing | Not in component docs; summary here | Use “Testing” above. |
| Runbooks / dashboard | Alarms in 10; runbooks here | Use “Dashboards & runbooks” above. |
| Cost | Yes | 00, 01, 05, 08. |
| Idempotency, retries, backpressure | Yes | 00, 02, 03, 04, 07, 09. |

Use this checklist with the component index in the README so you can answer “did you cover observability and other nuances?” with **yes for observability and most nuances**, and **short, rehearsed answers for security, re-drive, DR, testing, and runbooks** from this doc.
