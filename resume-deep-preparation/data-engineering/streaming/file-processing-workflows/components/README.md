# Equilend File Processing Workflow — Component Deep-Dive

**Purpose:** Interview prep for a **1+ hour deep technical discussion** on file processing workflows. Each component doc covers **pain points**, **data engineering metrics**, and **likely interview questions** so you can speak like a Solutions Architect.

**Your context:** You know PySpark; you did Glue but remember it vaguely; you haven’t worked on Snowflake (you have an idea from the AWS Data Engineering exam). Use these docs to fill gaps and rehearse nuances.

---

## How to Use

1. **Start with** `00-DATA-ENGINEERING-METRICS-AND-PAINPOINTS.md` — master list of metrics and cross-cutting pain points (exactly-once, Lambda vs Spark, ordering, backpressure, etc.).
2. **Then go component-by-component** in flow order when they drill down:
   - S3 → SNS/SQS → Orchestrator → DynamoDB → Glue (batch) → MSK → Spark Streaming → **Snowflake** → **API queries Snowflake directly**.
3. **For each component:** Read the “Interview Pain Points & Nuances” and “Likely Interview Questions & Answers” so you can answer confidently and use the right terms (visibility timeout, idempotency key, staging table, consumer lag, etc.).
4. **After components:** Use `11-NUANCES-CHECKLIST.md` to confirm coverage (observability, security, re-drive, DR, testing, runbooks) and rehearse any gaps.

---

## Component Index

| # | Doc | Component | Focus |
|---|-----|-----------|--------|
| 0 | `00-DATA-ENGINEERING-METRICS-AND-PAINPOINTS.md` | Master | Metrics, SLAs, cross-cutting pain points, “metrics by stage” |
| 1 | `01-S3.md` | S3 (landing, curated, quarantine) | Event type (Complete), multipart, consistency, prefix design, lifecycle |
| 2 | `02-SNS-SQS-EVENTING.md` | SNS + SQS | Visibility timeout, DLQ, at-least-once, message content |
| 3 | `03-ORCHESTRATION-LAMBDA-STEP-FUNCTIONS.md` | Orchestrator | Lambda vs Step Functions, idempotency check, invoking Glue |
| 4 | `04-DYNAMODB-AUDIT-IDEMPOTENCY.md` | DynamoDB (audit) | Key design, conditional writes, status transitions, consistency |
| 5 | `05-GLUE-PYSPARK-BATCH.md` | Glue + PySpark (batch) | Job bookmarks vs our pattern, DPUs, validation, S3 + Kafka write, DynamoDB update |
| 6 | `06-MSK-KAFKA.md` | MSK (Kafka) | Topics, partitioning, producer/consumer semantics, consumer lag |
| 7 | `07-SPARK-STREAMING-SNOWFLAKE.md` | Spark Streaming + Snowflake load | Micro-batch, staging table, MERGE by file_date, at-least-once |
| 8 | `08-SNOWFLAKE.md` | Snowflake | COPY INTO, clustering, MERGE; **API queries Snowflake directly** (connection pooling) |
| 9 | `09-GLUE-SYNC-RDBMS.md` | Optional: Snowflake → RDBMS sync | When you'd add it (scale, latency); not in our main flow |
| 10 | `10-OBSERVABILITY-CLOUDWATCH.md` | Observability | Logs, metrics, alarms, data pipeline metrics, lineage |
| 11 | `11-NUANCES-CHECKLIST.md` | Nuances checklist | What’s covered vs gaps: security, re-drive, DR, testing, runbooks |

---

## Quick Recap: End-to-End Flow (Memorize)

1. **Equilend** drops file to **S3** landing (`positions/`, `mtm_pnl/`).
2. **S3** `ObjectCreated:Complete` → **SNS** → **SQS**.
3. **Orchestrator** (Lambda or Step Functions) reads SQS, checks **DynamoDB** (idempotency); if not SUCCESS, starts **Glue** job with `--input-path`, `--file-type`, `--file-date`.
4. **Glue (PySpark)** reads CSV from S3, validates schema and business rules; valid rows → **curated S3** (Parquet) + **MSK** (Kafka); invalid → **quarantine** S3; updates **DynamoDB** (SUCCESS/PARTIAL/FAILED, record_count, reject_count).
5. **Spark Streaming** (or Glue Streaming) consumes from **MSK**, aggregates for reporting, sinks to **Snowflake** (staging → final; MERGE by file_date).
6. **Spring Boot API** on EKS queries **Snowflake** directly (JDBC + connection pooling; small warehouse or serverless). **Angular** app displays Daily Positions, Exposure by Borrower, Daily P&L.
7. **Observability:** CloudWatch logs/metrics/alarms; DynamoDB audit for lineage; DLQ and job failure alarms.

Use this README as the map; use each component doc for depth when they grill you on that part.
