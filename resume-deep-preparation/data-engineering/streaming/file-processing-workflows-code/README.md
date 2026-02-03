# Equilend File Processing Workflows — Code Implementation

End-to-end **code** implementation of the file-processing workflow (no infra). Flow follows the sequence below; each folder maps to one stage. Use with the companion docs under `../file-processing-workflows/`.

---

## Folder Structure (by Flow)

| Step | Folder | Role |
|------|--------|------|
| Start | **test-data/** | Sample CSV files (positions + mtm_pnl, 10–15 rows each). |
| 1 | **01-landing/** | Landing path config (S3 landing zone equivalent). |
| 2 | **02-eventing/** | File-arrival event / message model (S3 → SNS → SQS payload). |
| 3 | **03-orchestration/** | Orchestrator: idempotency check, reserve run, invoke batch processor. |
| 4 | **04-audit-idempotency/** | Audit store (DynamoDB equivalent): one row per file, status, counts. |
| 5 | **05-batch-processing/** | Batch processor: read CSV, validate, curated + quarantine, publish to event bus. |
| 6 | **06-event-bus/** | Event bus (Kafka/MSK equivalent): publish curated records. |
| 7 | **07-streaming-consumer/** | Streaming consumer: aggregate, sink to reporting store. |
| 8 | **08-reporting-store/** | Reporting store (Snowflake equivalent): daily_positions, exposure_by_borrower, daily_pnl. |
| 9 | **09-reporting-api/** | Spring Boot API: REST endpoints for the three reports. |

Java packages live under `src/main/java/workflow/equilend/` (eventing, orchestration, audit, batch, eventbus, streaming, reportingstore, api).

---

## Prerequisites

- **Java 11**
- **Maven 3.6+**

---

## How to Run

### 1. Run the pipeline (batch + streaming)

From the **project root** (`file-processing-workflows-code`):

```bash
mvn compile exec:java "-Dexec.mainClass=workflow.equilend.PipelineRunner"
```

- Reads **test-data/positions/** and **test-data/mtm_pnl/**.
- Validates rows; valid → **output/curated/** (CSV) + event bus; invalid → **output/quarantine/**.
- Streaming consumer aggregates and writes **output/reporting/** (JSON by report type and file_date).

### 2. Start the reporting API

```bash
mvn spring-boot:run
```

- Uses **output/reporting** as the reporting store (see `src/main/resources/application.properties`).
- Endpoints (after running the pipeline once):
  - `GET /api/reports/daily-positions?fileDate=20250201`
  - `GET /api/reports/exposure-by-borrower?fileDate=20250201`
  - `GET /api/reports/daily-pnl?fileDate=20250201`

---

## Test Data

- **test-data/positions/equilend_positions_20250201.csv** — 15 rows (schema: loan_id, trade_date, settle_date, security_id, borrower_id, quantity, loan_value_usd, …).
- **test-data/mtm_pnl/equilend_mtm_pnl_20250201.csv** — 15 rows (file_date, book_id, desk_id, loan_id, pnl_usd, …).

See **test-data/README.md** for schema and validation rules.

---

## Best Practices Reflected

- **Idempotency**: Orchestrator checks audit store before processing; reserve run with conditional put; complete only if status was PENDING.
- **Validation**: Schema and business rules per row; valid → curated + event bus; invalid → quarantine with reject reason.
- **Audit**: One record per file (idempotency key, status, record_count, reject_count, processed_at).
- **Flow separation**: Landing → eventing → orchestration → audit → batch → event bus → streaming → reporting store → API.
- **No infra**: All components are in-process / file-based (event bus in-memory, audit in-memory, reporting store file-backed).

---

## Production Mapping

| This repo | Production |
|-----------|------------|
| test-data/ | S3 landing bucket (positions/, mtm_pnl/) |
| FileArrivalEvent | SQS message (S3 ObjectCreated:Complete → SNS → SQS) |
| Orchestrator | Lambda or Step Functions |
| AuditStore | DynamoDB (or RDS) |
| FileProcessor | Glue/EMR PySpark job |
| EventBus | Amazon MSK (Kafka) |
| StreamingConsumer | Spark Streaming / Glue Streaming |
| ReportingStore | Snowflake (staging + MERGE by file_date) |
| ReportController | Spring Boot on EKS, JDBC to Snowflake |

---

## Study Order

Follow the flow: **test-data** → **01-landing** → **02-eventing** → **03-orchestration** → **04-audit-idempotency** → **05-batch-processing** → **06-event-bus** → **07-streaming-consumer** → **08-reporting-store** → **09-reporting-api**. Each numbered folder has a README describing that stage.
