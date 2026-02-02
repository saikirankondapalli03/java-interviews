# File Processing Workflow — Interview Question & Answer Banks

**Purpose:** Question banks and answer banks for each component of the Equilend-style file processing workflow. Structured to cover **all aspects of interview** (intro, architecture, technical depth, scenarios, follow-ups, evaluation)—leave no stone unturned.

**Reference:** The structure is inspired by the mock data engineering interview pattern: opening/context, architecture ownership, core technical depth, scenario/design, follow-ups (seniority), and evaluation cheat sheet.

---

## How to Use

1. **Start with** `components/00-DATA-ENGINEERING-METRICS-AND-PAINPOINTS.md` and the **00** Q&A bank for metrics and cross-cutting pain points.
2. **Go component-by-component** in flow order when they drill down:
   - S3 → SNS/SQS → Orchestrator → DynamoDB → Glue (batch) → MSK → Spark Streaming → Snowflake → (optional) RDBMS sync → Observability → Nuances.
3. **For each component:** Use the corresponding Q&A bank under `interview/`:
   - Read questions by section (Intro, Architecture, Technical Depth, Scenario, Follow-ups, Evaluation).
   - Rehearse answers out loud; use the component doc for depth.
4. **After components:** Use **11-NUANCES-CHECKLIST-QUESTION-ANSWER-BANK.md** for “what about security, re-drive, DR, testing, runbooks?” so you have short, rehearsed answers.
5. **Cross-cutting topics:** Use **12-SCD-QUESTION-ANSWER-BANK.md** for slowly changing dimensions (Type 1 vs Type 2, history, staging + MERGE, star schema dimensions).

---

## Q&A Bank Index

| # | Component | Q&A Bank File | Reference Component Doc |
|---|-----------|----------------|---------------------------|
| 0 | Data Engineering Metrics & Pain Points | `00-DATA-ENGINEERING-METRICS-QUESTION-ANSWER-BANK.md` | `00-DATA-ENGINEERING-METRICS-AND-PAINPOINTS.md` |
| 1 | S3 (landing, curated, quarantine) | `01-S3-QUESTION-ANSWER-BANK.md` | `01-S3.md` |
| 2 | SNS + SQS (eventing) | `02-SNS-SQS-QUESTION-ANSWER-BANK.md` | `02-SNS-SQS-EVENTING.md` |
| 3 | Orchestrator (Lambda / Step Functions) | `03-ORCHESTRATION-QUESTION-ANSWER-BANK.md` | `03-ORCHESTRATION-LAMBDA-STEP-FUNCTIONS.md` |
| 4 | DynamoDB (audit & idempotency) | `04-DYNAMODB-AUDIT-QUESTION-ANSWER-BANK.md` | `04-DYNAMODB-AUDIT-IDEMPOTENCY.md` |
| 5 | Glue + PySpark (batch) | `05-GLUE-PYSPARK-QUESTION-ANSWER-BANK.md` | `05-GLUE-PYSPARK-BATCH.md` |
| 6 | MSK (Kafka) | `06-MSK-KAFKA-QUESTION-ANSWER-BANK.md` | `06-MSK-KAFKA.md` |
| 7 | Spark Streaming + Snowflake load | `07-SPARK-STREAMING-SNOWFLAKE-QUESTION-ANSWER-BANK.md` | `07-SPARK-STREAMING-SNOWFLAKE.md` |
| 8 | Snowflake | `08-SNOWFLAKE-QUESTION-ANSWER-BANK.md` | `08-SNOWFLAKE.md` |
| 9 | Optional: Snowflake → RDBMS sync | `09-GLUE-SYNC-RDBMS-QUESTION-ANSWER-BANK.md` | `09-GLUE-SYNC-RDBMS.md` |
| 10 | Observability (CloudWatch) | `10-OBSERVABILITY-QUESTION-ANSWER-BANK.md` | `10-OBSERVABILITY-CLOUDWATCH.md` |
| 11 | Nuances checklist | `11-NUANCES-CHECKLIST-QUESTION-ANSWER-BANK.md` | `11-NUANCES-CHECKLIST.md` |
| 12 | Slowly Changing Dimensions (SCD) | `12-SCD-QUESTION-ANSWER-BANK.md` | Cross-cutting: Snowflake, Glue, star schema (08, 05, examples) |

---

## Structure of Each Q&A Bank

Each bank is organized so you can rehearse by **interview aspect**:

| Section | Content |
|---------|---------|
| **1. Intro & Context** | Role of component, why we use it, opening questions. |
| **2. Architecture & Ownership** | Design choices, key schema/APIs, tradeoffs. |
| **3. Technical Depth** | Pain points, metrics, implementation details. |
| **4. Scenario & Design** | “What if …?” and design questions. |
| **5. Follow-ups / Metrics** | Seniority signals, data engineering metrics for that component. |
| **6. Evaluation Cheat Sheet** | What good looks like vs red flags. |

---

## Quick Recap: End-to-End Flow (Memorize)

1. **Equilend** drops file to **S3** landing (`positions/`, `mtm_pnl/`).
2. **S3** `ObjectCreated:Complete` → **SNS** → **SQS**.
3. **Orchestrator** (Lambda or Step Functions) reads SQS, checks **DynamoDB** (idempotency); if not SUCCESS, starts **Glue** job with `--input-path`, `--file-type`, `--file-date`.
4. **Glue (PySpark)** reads CSV from S3, validates; valid rows → **curated S3** (Parquet) + **MSK** (Kafka); invalid → **quarantine** S3; updates **DynamoDB** (SUCCESS/PARTIAL/FAILED, record_count, reject_count).
5. **Spark Streaming** consumes from **MSK**, aggregates for reporting, sinks to **Snowflake** (staging → final; MERGE by file_date).
6. **Spring Boot API** on EKS queries **Snowflake** directly (JDBC + connection pooling). **Angular** app displays Daily Positions, Exposure by Borrower, Daily P&L.
7. **Observability:** CloudWatch logs/metrics/alarms; DynamoDB audit for lineage; DLQ and job failure alarms.

Use the component docs for depth; use these Q&A banks to rehearse full interview coverage.
