# Agency Lending – Equilend File Processing Workflow (Reporting)
## Interview Prep: Business & Technical Requirements, Design & Rationale (Spark + Kafka, AWS)

This document supports your **Senior Backend Engineer** interview for Fidelity Agency Lending file processing workflows. Use case: **reporting**. Stack: **Spark (batch + streaming) + Kafka**, **AWS cloud–centric**. Deep enough to sustain a **1-hour** technical and design discussion.

**Reporting warehouse:** **Snowflake**. Pipeline: Glue/Spark → Kafka → Spark Streaming → Snowflake → **API queries Snowflake directly**. No Glue sync to RDBMS for our use case. Use component doc `08-SNOWFLAKE.md` for depth.

---

# RESUME LINE — HOW TO EXPLAIN (Resume Already Submitted)

**Your bullet:**  
*"Architected file processing workflows using Spring Boot; containerized and deployed to Amazon EKS; integrated S3 feeds via SNS for event-driven orchestration"*

**You're worried because:** The deep-doc says file *processing* (parsing, validation) is Spark/EMR/Glue, not Spring Boot. So how do you square the resume?

**Short answer:** You're fine. The resume is **accurate** if you own it the right way.

- **"File processing workflows"** = the end-to-end flow that takes files from S3 and delivers reportable data to users. That flow **includes** Spring Boot as the **reporting API layer** (the part you containerized and deployed to EKS). You didn't say "implemented file parsing in Spring Boot."
- **"Using Spring Boot"** = Spring Boot is the API that serves the Angular app (Daily Positions, Exposure by Borrower, Daily P&L). It's a core part of the workflow architecture.
- **S3 + SNS + EKS** = Correct: S3 feeds, SNS for event-driven orchestration, Spring Boot APIs on EKS. All true.

**If they ask: "What did Spring Boot do in this workflow?"**  
*"Spring Boot is the **reporting API** we deployed on EKS. It doesn't do the heavy file parsing—that runs on Spark (EMR or Glue) because the files can be large and we need distributed processing. Spring Boot queries **Snowflake** directly (JDBC + connection pooling) and serves the Angular app: Daily Positions, Exposure by Borrower, Daily P&L. So in this workflow, Spring Boot is the serving layer; the batch processing layer is Spark. We integrated S3 feeds via SNS so that when files land, the event-driven pipeline runs, and the API has the data available from Snowflake for the UI."*

**If they ask: "So you didn't use Spring Boot for the actual file processing?"**  
*"Right—the CSV read, validation, and publish to Kafka is Spark. Spring Boot's role is the API that exposes the processed data to the front end. We architected the **overall** workflow so that file ingestion, processing, and serving are clearly separated; Spring Boot owns the serving part and is what we containerized and ran on EKS."*

**One sentence you can use anytime:**  
*"I architected the file processing workflow end-to-end: S3 and SNS for event-driven orchestration, Spark for the actual file processing and Kafka pipeline, and Spring Boot on EKS as the reporting API that serves the UI from Snowflake."*

Use this so you can answer confidently and honestly without overclaiming or underclaiming.

---

# PART A: DOMAIN & REPORTING CONTEXT

## A.1 Agency Lending in One Paragraph

Fidelity’s Agency Lending business acts as **agent** for institutional lenders (pension funds, asset managers, banks) who want to earn income on securities they hold. Borrowers (hedge funds, market makers, broker-dealers) borrow those securities for short selling, arbitrage, or settlement. Fidelity matches lenders and borrowers, negotiates terms (rebate rate, fee, collateral), and settles trades. Equilend is the **industry platform** many agents and borrowers use for trading, settlement, and post-trade data. Equilend does not hold the data long-term; they produce **files** (positions, P&L, collateral) and drop them into the client’s environment—in our case, an **S3 bucket**—so the client can run **reporting**, risk, and operations.

## A.2 What Reports Make Sense in This Domain?

| Report | Purpose | Who cares | Typical questions |
|--------|---------|-----------|--------------------|
| **Daily Loan Positions** | What’s lent out as of EOD: which securities, to which borrowers, at what rate, what value. | Ops, Risk, Management | “What’s our exposure by borrower?” “What’s on loan by security?” |
| **Exposure by Borrower** | Aggregate loan value and count by borrower (concentration risk). | Risk, Compliance | “Are we over-concentrated with one borrower?” |
| **Daily P&L / Mark-to-Market** | Daily P&L by book/desk from rebate, fees, and mark-to-market. | Finance, Management | “What did agency lending earn today?” |
| **Collateral Summary** | Cash vs non-cash collateral received; margin usage. | Ops, Risk | “Do we have enough collateral?” “What’s our margin?” |
| **Settlement / Activity** | Trades settled, failed, or pending (reconciliation). | Ops, Middle office | “Did everything settle?” “What’s still open?” |

**End objective (one sentence):**  
Process Equilend files from S3 so that **daily positions, exposure, P&L, and collateral** are available in a **reporting store** (e.g., **Snowflake** or S3 + Athena) and **Angular app** (reporting UI) for **Operations, Risk, Finance, and Management** by T+1 morning.

**Who displays the data?**

| Persona | Where they see it | What they see |
|---------|-------------------|---------------|
| **Operations** | Angular app – “Daily Positions” | Positions by security, borrower, book; record counts; file receipt status. |
| **Risk** | Angular app – “Exposure by Borrower” | Loan value and count by borrower; concentration; limits. |
| **Finance / Management** | Angular app – “Daily P&L” | P&L by book/desk; rebate and fee income; MTM change. |
| **Compliance / Audit** | Athena/Snowflake queries + audit table | Raw file lineage; processed file list; record counts; checksums. |

---

## A.3 Why This Scale Is Believable (So the Pipeline Doesn’t Sound Overkill)

**Objective:** Interviewers may have worked on Equilend or similar file-based reporting. If the business requirements don’t feel real, a big pipeline (Spark, Glue, Kafka, DynamoDB audit, etc.) sounds over-engineered. Establish believability first so the design makes sense.

| What we claim | Why it’s believable (for someone who knows the domain) |
|---------------|--------------------------------------------------------|
| **Positions file: 10K–2M rows** | One row ≈ one loan position (security + borrower + book + terms). Large agents have tens of thousands of open loans, hundreds of counterparties, thousands of securities. 100K–500K rows/day is normal; very large or granular books can hit 1M–2M. |
| **P&L file: 1K–100K rows** | Aggregated by book/desk/loan—still tens of thousands of rows for a large book. Enough to justify batch processing, not row-by-row in a single app. |
| **Equilend drops files into our S3** | Equilend is a platform; they don’t run our reporting. They produce files (positions, P&L, collateral) and deliver to the client (e.g. S3 or SFTP). EOD file drops for reporting/risk/ops are standard in finance. |
| **T+1 morning SLA** | Ops, Risk, and Finance need reports by next morning. File drops EOD; pipeline must finish in the overnight window. That’s why we need Spark (throughput), idempotency (re-drops/replays), and observability (catch failures before 6 AM). |

**One line to use if they push back on “why so much?”**  
*“The positions file can be hundreds of thousands of rows for a large book—Equilend drops the full EOD snapshot. We need distributed processing to hit T+1, and idempotency/audit because re-drops and replays happen. So the pipeline is sized for that reality.”*

---

# PART B: CONCRETE FILE SPECIFICATIONS (What File? What Fields?)

Equilend drops files into a **landing bucket** with a known prefix. We focus on **two report types** that drive the main reporting use case.

---

## B.1 File 1: Daily Loan Positions (Equilend Positions Report)

**Business purpose:** “As of EOD, what securities do we have on loan, to whom, at what rate, and what value?” This is the **source for positions and exposure reporting**.

**File identity**

| Attribute | Value |
|-----------|--------|
| **Logical name** | Equilend Daily Loan Positions |
| **File naming pattern** | `equilend_positions_YYYYMMDD.csv` (e.g. `equilend_positions_20250128.csv`) |
| **S3 location** | `s3://fidelity-agency-lending-equilend-landing/positions/equilend_positions_YYYYMMDD.csv` |
| **Format** | CSV, header row, pipe or comma delimiter (configurable) |
| **Frequency** | Once per business day, dropped after market close (e.g. 6–8 PM ET) |
| **Size (typical)** | 10K–2M rows depending on volume |

**Schema (fields inside the file)**

| Column name | Data type | Mandatory | Description / validation |
|-------------|-----------|-----------|---------------------------|
| `loan_id` | string | Yes | Unique loan identifier from Equilend; primary key for dedup. |
| `trade_date` | date (YYYY-MM-DD) | Yes | Trade date. |
| `settle_date` | date (YYYY-MM-DD) | Yes | Settlement date. Must be >= trade_date. |
| `security_id` | string | Yes | Instrument identifier (e.g. ISIN or CUSIP). |
| `security_name` | string | No | Human-readable name. |
| `borrower_id` | string | Yes | Borrower identifier (e.g. MPID or internal code). |
| `borrower_name` | string | No | Borrower legal name. |
| `quantity` | decimal(18,4) | Yes | Number of shares/units on loan. Must be > 0. |
| `loan_value_usd` | decimal(18,2) | Yes | Market value of loan in USD. Must be >= 0. |
| `rebate_rate_bps` | decimal(10,4) | Yes | Rebate rate in basis points (can be negative). |
| `fee_rate_bps` | decimal(10,4) | No | Fee rate in basis points. |
| `collateral_type` | string | Yes | E.g. CASH, NON_CASH, MIXED. Enum from Equilend. |
| `collateral_value_usd` | decimal(18,2) | Yes | Collateral held against this loan. |
| `book_id` | string | Yes | Internal book (desk/product). |
| `desk_id` | string | Yes | Trading desk. |
| `source_system` | string | Yes | Literal "EQUILEND" for this feed. |
| `file_date` | date (YYYY-MM-DD) | Yes | Business date of the file (as of which EOD). |

**Validation rules (technical):**

- No duplicate `loan_id` within the same file.
- `quantity` > 0, `loan_value_usd` >= 0, `settle_date` >= `trade_date`.
- `security_id`, `borrower_id`, `book_id`, `desk_id` non-empty.
- Date columns parseable as date; numeric columns parseable as decimal.
- Row count and optional file-level checksum (e.g. MD5) for idempotency and audit.

---

## B.2 File 2: Daily Mark-to-Market / P&L Summary

**Business purpose:** “What was the daily P&L by book/desk from rebate, fees, and mark-to-market?” This feeds the **Daily P&L report** for Finance and Management.

**File identity**

| Attribute | Value |
|-----------|--------|
| **Logical name** | Equilend Daily P&L / Mark-to-Market |
| **File naming pattern** | `equilend_mtm_pnl_YYYYMMDD.csv` |
| **S3 location** | `s3://fidelity-agency-lending-equilend-landing/mtm_pnl/equilend_mtm_pnl_YYYYMMDD.csv` |
| **Format** | CSV, header row |
| **Frequency** | Once per business day, after positions file (or same batch). |
| **Size (typical)** | 1K–100K rows (aggregated by book/desk/loan). |

**Schema (fields inside the file)**

| Column name | Data type | Mandatory | Description / validation |
|-------------|-----------|-----------|---------------------------|
| `file_date` | date (YYYY-MM-DD) | Yes | Business date. |
| `book_id` | string | Yes | Book. |
| `desk_id` | string | Yes | Desk. |
| `loan_id` | string | Yes | Links to positions file. |
| `mtm_value_prev_usd` | decimal(18,2) | No | Prior day MTM value. |
| `mtm_value_current_usd` | decimal(18,2) | Yes | Current day MTM value. |
| `pnl_usd` | decimal(18,2) | Yes | Daily P&L for this loan (MTM + rebate/fee). |
| `rebate_income_usd` | decimal(18,2) | No | Rebate income component. |
| `fee_income_usd` | decimal(18,2) | No | Fee income component. |
| `source_system` | string | Yes | "EQUILEND". |

**Validation rules:** Numerics parseable; `file_date`, `book_id`, `desk_id`, `loan_id` non-empty; no duplicate `(file_date, book_id, desk_id, loan_id)`.

---

## B.3 File Naming and Idempotency Key

- **Idempotency key:** `source_system` + `file_type` + `file_date` + `file_name`  
  Example: `EQUILEND|positions|20250128|equilend_positions_20250128.csv`  
  Before processing, we check the **audit table** (e.g. in DynamoDB or RDS). If this key exists and status = SUCCESS, we **skip** (or optionally overwrite with same semantics) so re-drops or replays do not duplicate data in reporting.

---

# PART C: BUSINESS REQUIREMENTS (Deeper)

## C.1 Ingestion & Trigger

| ID | Requirement | Detail |
|----|-------------|--------|
| BR-1 | Ingest Equilend files from the designated S3 landing bucket as they arrive. | **Which bucket:** e.g. `fidelity-agency-lending-equilend-landing`. **Which prefixes:** `positions/`, `mtm_pnl/`. **When:** On `s3:ObjectCreated:Complete` (file fully written). No polling. |
| BR-2 | Process files within SLA so reports are available by T+1 morning. | **SLA:** Positions and P&L data available for reporting by 6 AM ET on T+1. **Implication:** File processing + Spark job + Kafka pipeline + load to Snowflake/Athena must complete within that window. |

## C.2 Data Quality & Validation

| ID | Requirement | Detail |
|----|-------------|--------|
| BR-3 | Validate file format and schema before persisting. | **Positions file:** All mandatory columns present; types correct; no duplicate `loan_id`; `quantity` > 0; `loan_value_usd` >= 0; `settle_date` >= `trade_date`; `security_id`, `borrower_id`, `book_id`, `desk_id` non-empty. **P&L file:** Mandatory columns present; `pnl_usd`, `mtm_value_current_usd` parseable; no duplicate (file_date, book_id, desk_id, loan_id). Invalid rows go to **reject** path (e.g. S3 quarantine + audit); valid rows proceed. |
| BR-4 | Idempotent processing. | Same file (same idempotency key) re-dropped or replayed must not duplicate records in reporting. Either skip or overwrite by `file_date` + `file_type` so end state is deterministic. |
| BR-5 | Audit trail. | For each file: `file_name`, `s3_uri`, `file_date`, `file_type`, `record_count`, `reject_count`, `checksum` (optional), `processed_at`, `status` (SUCCESS / PARTIAL / FAILED), `processing_job_id`. Stored in **audit table** (e.g. DynamoDB or RDS) and optionally in S3 manifest for compliance. |
| BR-6 | Partial failure handling. | One bad file or one bad row must not block other files or other rows. Bad files → quarantine + DLQ; bad rows → reject file or reject partition; good data still loaded. |
| BR-7 | Failure notification. | Invalid files or validation failures must be visible to ops: **SNS topic** (e.g. `equilend-file-failures`) and/or **quarantine S3 prefix** + **CloudWatch alarm** so someone can correct data or follow up with Equilend. |

## C.3 Reporting (End Objective)

| ID | Requirement | Detail |
|----|-------------|--------|
| BR-8 | Daily Loan Positions report. | **Data source:** Processed positions file (curated). **Consumers:** Ops, Risk. **Display:** Angular app – “Daily Positions” (positions by security, borrower, book; filters by date). **Metrics:** Total loan value, count of loans, top borrowers, top securities. |
| BR-9 | Exposure by Borrower report. | **Data source:** Same positions data, aggregated by `borrower_id` (sum of `loan_value_usd`, count of loans). **Consumers:** Risk, Compliance. **Display:** Angular app “Exposure by Borrower”. **Purpose:** Concentration risk. |
| BR-10 | Daily P&L report. | **Data source:** Processed P&L file (curated). **Consumers:** Finance, Management. **Display:** Angular app “Daily P&L” (P&L by book/desk; rebate and fee income). **Metrics:** Total P&L, P&L by book, by desk. |
| BR-11 | Data available for ad-hoc query. | **Where:** Snowflake or Snowflake (Fidelity uses Snowflake), or S3 + Athena. **Who:** Compliance, Audit, analysts. **What:** Curated positions and P&L tables + audit table for lineage. |

---

# PART D: TECHNICAL REQUIREMENTS (Deeper, AWS-Centric)

## D.1 Trigger & Eventing (AWS)

| ID | Requirement | Detail |
|----|-------------|--------|
| TR-1 | Trigger on S3 object creation. | **Bucket:** e.g. `fidelity-agency-lending-equilend-landing`. **Event:** `s3:ObjectCreated:Complete` (or equivalent) so we do not process multipart upload start. **Prefix filter:** `positions/`, `mtm_pnl/` so only Equilend files trigger the pipeline. |
| TR-2 | Event-driven notification. | **Option A:** S3 Event Notifications → **SNS** topic `equilend-file-arrival` → **SQS** queue `equilend-positions-queue` / `equilend-mtm-pnl-queue` (or one queue with message attributes for type). **Option B:** S3 → **EventBridge** rule (pattern on bucket/prefix) → SQS or Lambda. **Rationale:** Decouple “file arrived” from “process”; allow retries, DLQ, and scaling. |
| TR-3 | Message content. | SQS message body (or EventBridge detail) must include at least: `bucket`, `key`, `file_size`, `event_time`. Optional: `etag` for idempotency. |

## D.2 Processing Engine: Spark (Batch) + Kafka (Streaming) – AWS

| ID | Requirement | Detail |
|----|-------------|--------|
| TR-4 | **Batch processing of each file with Apache Spark.** | **Where:** **Amazon EMR** (Spark on YARN) or **AWS Glue** (Spark jobs). **Flow:** SQS message received → orchestrator (Lambda or Step Functions) submits **Spark job** (PySpark) with args: `--input-path s3://bucket/key`, `--file-type positions|mtm_pnl`, `--file-date YYYYMMDD`. Spark reads CSV from S3, validates schema and business rules, writes **valid** rows to **curated S3** (Parquet) and/or **publishes to Amazon MSK** (Kafka) for the reporting pipeline. **Rationale:** Large files (millions of rows), complex validation and transforms; Spark gives scalable, distributed batch processing. |
| TR-5 | **Streaming pipeline for reporting using Kafka + Spark Streaming.** | **Source:** **Amazon MSK** (Kafka). **Topics:** e.g. `equilend.positions.curated`, `equilend.mtm_pnl.curated`. Spark batch job (above) **writes** to these topics (per record or micro-batch). **Consumer:** **Spark Streaming** (on EMR or EKS) or **Glue Streaming** job: reads from MSK, aggregates (e.g. by borrower, by book), and **sinks** to **Snowflake** (via JDBC/connector or COPY INTO from S3) or **S3** (Parquet) for **Athena**, or **OpenSearch** for search-style dashboards. **Rationale:** Unify file-originated data with other event streams later; support near–real-time reporting if we add more sources; you use **both Spark (batch) and Kafka (streaming)** in one pipeline. |
| TR-6 | Schema and file type configuration. | **Positions:** Column list, types, mandatory flags, and validation rules in **config** (e.g. S3 JSON/YAML or Parameter Store). **P&L:** Same. **File type detection:** From S3 key prefix or naming pattern (`positions/` vs `mtm_pnl/`). |
| TR-7 | Idempotency. | **Key:** `EQUILEND|<file_type>|<file_date>|<s3_key>`. **Store:** **DynamoDB** table `equilend_file_audit` (partition key: `file_type#file_date`, sort key: `s3_key`) or RDS. Before Spark job writes to Kafka or Snowflake, check DynamoDB; if key exists and status = SUCCESS, skip write or overwrite same partition. Spark job updates DynamoDB on success/failure. |
| TR-8 | Staged load and consistency. | **Curated S3:** Spark writes to **staging prefix** (e.g. `s3://curated-bucket/equilend/positions/file_date=YYYYMMDD/`) then **move/commit** so downstream (Athena, Snowflake COPY INTO) sees only complete partitions. **Snowflake:** Use **staging table** then **INSERT INTO final** or **MERGE** by `file_date` + `file_type` so we do not expose partial writes (Snowflake has strong MERGE support). |
| TR-9 | Resource and cost control. | **EMR:** Transient cluster per job or long-running cluster with auto-scaling. **Glue:** Job bookmark and worker count. **Concurrency:** SQS visibility timeout and max in-flight messages so the same file is not processed twice; limit concurrent Spark jobs (e.g. one positions + one P&L at a time per day). |

## D.3 Observability & Failure (AWS)

| ID | Requirement | Detail |
|----|-------------|--------|
| TR-10 | Logging and metrics. | **CloudWatch Logs:** EMR/Glue driver logs; application logs (file received, validation pass/fail, record counts, job duration). **CloudWatch Metrics:** Custom metrics (e.g. `EquilendFilesProcessed`, `EquilendRowsValid`, `EquilendRowsRejected`, `ProcessingLatency`). **X-Ray:** Optional tracing across Lambda → EMR/Glue → MSK. |
| TR-11 | Dead-letter and quarantine. | **SQS DLQ:** `equilend-file-dlq` for messages that fail after max receives (e.g. bad path, repeated Spark failure). **S3 quarantine:** Invalid or rejected rows written to `s3://.../quarantine/equilend/<file_type>/<file_date>/`. **Alert:** SNS topic `equilend-file-failures` + CloudWatch alarm on DLQ depth or quarantine prefix object count. |
| TR-12 | Audit table. | **DynamoDB** (or RDS): `file_id`, `s3_uri`, `file_type`, `file_date`, `record_count`, `reject_count`, `processed_at`, `status`, `job_id`. Queried by Compliance and by idempotency check. |

## D.4 Security & Compliance (AWS)

| ID | Requirement | Detail |
|----|-------------|--------|
| TR-13 | Least-privilege IAM. | **EMR/Glue role:** Read S3 landing + config; write S3 curated + quarantine; write MSK; write DynamoDB audit; write CloudWatch. **Lambda/Step Functions role:** Receive SQS; invoke EMR/Glue; read/write DynamoDB. **No** cross-account or broad S3 wildcards unless required. |
| TR-14 | Encryption and masking. | **S3:** SSE-S3 or SSE-KMS on landing, curated, quarantine. **MSK:** TLS in transit; encryption at rest. **Logs:** Mask `borrower_name`, PII in application logs. **Snowflake/Athena:** Column-level or table-level access (or Snowflake masking) for sensitive reports. |

---

# PART E: ARCHITECTURE (Spark + Kafka, AWS-Centric)

## E.1 End-to-End Flow (Reporting Use Case)

```
Equilend
    → drops file to S3 landing
        s3://fidelity-agency-lending-equilend-landing/positions/equilend_positions_YYYYMMDD.csv
        s3://.../mtm_pnl/equilend_mtm_pnl_YYYYMMDD.csv

S3 ObjectCreated (Complete)
    → SNS topic (equilend-file-arrival)
        → SQS (equilend-file-queue)

Orchestrator (Lambda or Step Functions)
    → checks idempotency (DynamoDB)
    → submits Spark job (EMR or Glue) with bucket, key, file_type, file_date

Spark (PySpark) – BATCH
    → reads CSV from S3
    → validates schema and business rules (positions or P&L)
    → writes valid rows:
        (1) S3 curated (Parquet) → for Athena / historical
        (2) Amazon MSK (Kafka) topics: equilend.positions.curated, equilend.mtm_pnl.curated
    → writes invalid rows to S3 quarantine
    → updates DynamoDB audit (SUCCESS / PARTIAL / FAILED)

Spark Streaming (or Glue Streaming) – STREAMING
    → consumes from MSK (equilend.positions.curated, equilend.mtm_pnl.curated)
    → aggregates (e.g. by borrower_id, by book_id) for reporting
    → sinks to:
        (1) Snowflake or Snowflake (Fidelity) (reporting tables: daily_positions, exposure_by_borrower, daily_pnl)
        (2) or S3 (Parquet) for Athena

Reporting & display (our pattern)
    → Snowflake (source of truth for analytics and API)
    → Spring Boot API (JDBC to Snowflake; connection pooling; simple SELECTs)
    → Angular app: Daily Positions, Exposure by Borrower, Daily P&L screens
    → Personas: Ops, Risk, Finance, Management (see Part A)
```

## E.4 Reporting API Layer: How Does Spring Boot Get the Data? (Architect Recommendation)

**Short answer:** The **Spring Boot API queries Snowflake directly** via JDBC with connection pooling. No Glue sync to an RDBMS for our use case (internal reporting, T+1, moderate concurrency). One source of truth.

### Why not Spring Boot → Snowflake directly?

| Concern | Snowflake direct (JDBC) | RDBMS as reporting serving layer |
|--------|------------------------|-----------------------------------|
| **Connection limits** | Snowflake has limited concurrent connections per cluster; many API instances × pool size can exhaust them. | PostgreSQL/Aurora is built for many concurrent OLTP-style connections. |
| **Workload fit** | Snowflake is a columnar analytical DB—best for bulk scans and analytics, not high-frequency, low-latency API traffic. | RDBMS is optimized for indexed point lookups and small result sets (exactly what “get this screen’s data” needs). |
| **Latency** | Analytical queries can be hundreds of ms to seconds; acceptable for ad-hoc, not ideal for snappy UI. | Simple SELECTs on indexed tables give sub-second response for dashboard loads. |
| **Operational clarity** | Mixing “analytics” and “API serving” on the same cluster blurs SLAs and makes tuning harder. | Clear separation: Snowflake = analytics/source of truth; RDBMS = reporting serving layer. |

**Conclusion:** Spring Boot *can* call Snowflake via JDBC (or Snowflake Data API) for **small scale** (e.g. few concurrent users, infrequent report loads). For a **production reporting UI** with multiple personas (Ops, Risk, Finance), the **recommended** pattern is to **sync aggregates to an RDBMS** and have the API query that.

### Recommended pattern: Glue sync → RDBMS → Spring Boot

1. **Snowflake** (or S3 + Athena) remains the **source of truth** for the full dataset (Spark Streaming loads it; compliance and ad-hoc analytics query it).
2. **Glue job (or scheduled Spark job)** runs **after** the main Equilend pipeline has loaded the warehouse (e.g. on a schedule post–T+1 load, or triggered by pipeline completion). It:
   - Reads from Snowflake (or from the same S3 curated Parquet that Snowflake is loaded from) the **reporting aggregates** needed for the three screens: `daily_positions`, `exposure_by_borrower`, `daily_pnl`.
   - Writes them into **PostgreSQL** (RDS or **Aurora**) in tables such as `reporting.daily_positions`, `reporting.exposure_by_borrower`, `reporting.daily_pnl` (same grain as the reports; optionally partitioned by `file_date`).
3. **Spring Boot API** connects to the RDBMS via JDBC (or JPA). It runs **simple SELECTs** (e.g. by `file_date`, `borrower_id`) or, if logic is complex, **stored procedures** that return result sets for each report screen.
4. **Angular app** calls the API; the API returns JSON; no direct DB access from the front end.

### Do we need an RDBMS? Why not just S3 or Athena?

- **RDBMS** gives **flexible querying** (filters, pagination, “show me borrower X across dates”) without pre-generating every possible report file. Indexes and optional stored procedures keep API logic simple and fast. For “serve pre-aggregated reporting data to an API with multiple screens and filters,” an RDBMS is the right fit.
- **S3 + pre-generated JSON/Parquet** (API reads from S3 or via Athena) works when reports are **fixed** (e.g. one file per report per date). It’s simpler but less flexible for ad-hoc filters and multi-dimensional drill-downs. Acceptable for a minimal MVP; RDBMS is better when the product demands richer interaction.
- **Athena** is great for ad-hoc SQL on S3; it is not designed for high-frequency, low-latency API traffic (query latency and concurrency limits). So we don’t have the API call Athena directly for each request.

### Stored procedures: when to use

- **Simple reports:** Tables + indexes + parameterized SELECTs in Spring Boot are enough (e.g. “positions for date X,” “exposure by borrower for date X”). No stored procedures required.
- **Complex logic:** If a report needs multi-table joins, heavy aggregation, or business rules that you want in one place, **stored procedures** in the RDBMS are appropriate. The API calls a procedure and returns the result set as JSON. Use them when they reduce duplication and keep reporting logic centralized.

### One-line for interview (API layer)

“We **don’t** have the API hit Snowflake directly—Snowflake is our analytical source of truth with limited connections. A **Glue job** (or Spark) syncs the reporting aggregates from Snowflake into **PostgreSQL/Aurora**; the **Spring Boot API** queries that RDBMS via JDBC. So: Snowflake → Glue sync → RDBMS → Spring Boot → Angular. Stored procedures are optional for complex report logic; simple SELECTs are enough for the three main screens.”

## E.2 Why Spark (Batch) + Kafka (Streaming) Together

| Component | Role | Why |
|-----------|------|-----|
| **Spark (batch)** | Read file from S3; validate; transform; write to S3 + **publish to Kafka**. | Files are large and batch by nature; Spark scales and fits complex validation/joins. |
| **Kafka (MSK)** | Event bus for **curated** positions and P&L. | Single place for “reporting-ready” data; multiple consumers (Spark Streaming, future real-time dashboards, risk engine). |
| **Spark Streaming** | Consume from Kafka; aggregate; sink to Snowflake/S3. | **Reporting** use case: we need aggregates (by borrower, by book) and stable tables for the API consumed by the Angular app. Spark Streaming gives exactly-once or at-least-once semantics and fits your skills. |

**One-line for interview:** “We use **Spark batch** to process the Equilend file from S3 and publish curated records to **Kafka (MSK)**; **Spark Streaming** consumes from Kafka, does the reporting aggregates, and loads **Snowflake**. The **Spring Boot API** queries Snowflake directly (JDBC + connection pooling) and serves the **Angular app** that displays Daily Positions, Exposure by Borrower, and Daily P&L for Ops, Risk, and Finance.”

## E.3 AWS Services Summary

| Layer | AWS service |
|-------|-------------|
| Landing | **S3** (landing bucket, prefixes positions/ mtm_pnl/) |
| Event | **S3 Event Notifications** → **SNS** → **SQS** (or **EventBridge** → SQS) |
| Orchestration | **Lambda** or **Step Functions** (receive SQS, check DynamoDB, submit Spark job) |
| Batch processing | **Amazon EMR** (Spark/PySpark) or **AWS Glue** (Spark job) |
| Event bus / streaming | **Amazon MSK** (Kafka): topics equilend.positions.curated, equilend.mtm_pnl.curated |
| Streaming processing | **EMR** (Spark Streaming) or **Glue Streaming** consuming from MSK |
| Reporting store (source of truth) | **Snowflake** – analytics, ad-hoc, compliance, **and API** |
| API layer | **Spring Boot** (JDBC to Snowflake; connection pooling; simple SELECTs) – **queries Snowflake directly** |
| Front-end / dashboards | **Angular app** (consumes Spring Boot API; Daily Positions, Exposure by Borrower, Daily P&L screens) |
| Audit / idempotency | **DynamoDB** (file_audit table) |
| Quarantine / DLQ | **S3** (quarantine prefix), **SQS DLQ** |
| Observability | **CloudWatch** (logs, metrics, alarms), **SNS** (failure alerts) |

---

# PART F: INTERVIEW TALKING POINTS (1-Hour Depth)

## F.1 Business (Concrete)

1. **Domain:** “Agency lending: we lend securities on behalf of institutional clients; Equilend is the platform that gives us post-trade data. They drop files to our S3 bucket EOD.”
2. **Files:** “Two main files for reporting: **positions**—every loan as of EOD, with security, borrower, quantity, value, rate, collateral—and **P&L**—daily mark-to-market and income by book/desk. Both CSV, with clear schemas and validation rules.”
3. **End objective:** “The end objective is **reporting**: Daily Positions, Exposure by Borrower, and Daily P&L in our **Angular app** for Ops, Risk, and Finance by T+1 morning.”
4. **Who displays:** “Ops sees positions and file health; Risk sees exposure by borrower; Finance sees P&L by book. Data is synced from Snowflake into an RDBMS; the Spring Boot API queries the RDBMS and serves it to the Angular app—we don't have the API hit Snowflake directly.”

## F.2 Technical (Concrete)

5. **Trigger:** “Equilend drops to S3. We use **S3 event notifications** to **SNS**, then **SQS**, so we don’t poll. Only `ObjectCreated:Complete` and only on the positions/ and mtm_pnl/ prefixes.”
6. **Spark + Kafka:** “We use **Spark batch** (EMR or Glue) to read the file from S3, validate every field—loan_id, quantity, dates, borrower_id, etc.—and write valid rows to **curated S3** (Parquet) and to **Amazon MSK** (Kafka). **Spark Streaming** then consumes from Kafka, aggregates for reporting (e.g. by borrower, by book), and loads **Snowflake** (or S3 for Athena). So we use **both Spark and Kafka**: batch for file processing, streaming for the reporting pipeline.”
7. **Idempotency:** “We key off source + file type + file date + S3 key. We check **DynamoDB** before running the Spark job; if we already processed that file successfully, we skip. So re-drops don’t duplicate data in Snowflake or Kafka.”
8. **Failure:** “Invalid rows go to **S3 quarantine**; invalid files or repeated failures go to **SQS DLQ**. We write an **audit** row in DynamoDB for every file (record count, reject count, status). We have an **SNS** topic and **CloudWatch** alarm so ops get notified.”

## F.3 Design Rationale (Short)

9. **Why Kafka in a file workflow?** “So that reporting and other consumers see a single stream of curated data; we can add more sources later (e.g. another vendor) and still have one pipeline to Snowflake. The API queries Snowflake directly. Plus it fits our goal to use **both Spark and Kafka** for reporting.”
10. **Why Spark for the file?** “Files can be large (millions of rows) and we have non-trivial validation and transforms. Spark gives us distributed, scalable batch processing and a clear path to Spark Streaming for the Kafka side.”

---

# PART G: FOLLOW-UP QUESTIONS & ANSWERS

- **What’s in the positions file?**  
  Loan-level rows: loan_id, trade_date, settle_date, security_id, borrower_id, quantity, loan_value_usd, rebate_rate_bps, collateral_type, collateral_value_usd, book_id, desk_id, file_date. We validate types, no duplicate loan_id, quantity > 0, settle_date >= trade_date.

- **Who uses the reports?**  
  Ops (Daily Positions), Risk (Exposure by Borrower), Finance (Daily P&L). All via the Angular app, which consumes the Spring Boot API; the API reads from **Snowflake** directly (JDBC + connection pooling).

- **How does the Spring Boot API query the data? Does it call Snowflake?**  
  No. In the recommended production pattern, the API **does not** call Snowflake. A **Glue job** (or Spark) syncs reporting aggregates from Snowflake into an **RDBMS** (PostgreSQL or Aurora). The Spring Boot API queries the RDBMS via JDBC (simple SELECTs or stored procedures). Snowflake stays the analytical source of truth; the RDBMS is the reporting serving layer for the UI.

- **Why use an RDBMS? Why not keep everything in Snowflake?**  
  Snowflake has limited concurrent connections and is optimized for analytical workloads, not high-frequency API traffic. The RDBMS gives many connections, indexed lookups, and sub-second response for “get this screen’s data.” We use it only for the pre-aggregated reporting tables the API needs—not as the source of truth. Stored procedures are optional when report logic is complex.

- **Do you recommend a Glue job to dump Snowflake to RDBMS?**  
  Yes. A **Glue job** (or scheduled Spark job) that runs after the main pipeline has loaded Snowflake is the right way to sync reporting aggregates (daily_positions, exposure_by_borrower, daily_pnl) into PostgreSQL/Aurora. The API then reads from the RDBMS. This keeps Snowflake for analytics and gives the UI a fast, connection-friendly serving layer.

- **Why not just S3 → Lambda → Snowflake?**  
  File size and complexity: Lambda has time and memory limits; validation and transforms are easier and more scalable in Spark. We also need to publish to Kafka for the streaming reporting pipeline.

- **Why MSK and not Kinesis?**  
  We wanted a Kafka-compatible bus so we can use **Spark Streaming** (or Kafka Streams) with the same APIs we’re used to; MSK is managed Kafka on AWS.

- **How do you avoid duplicate data when the same file is dropped twice?**  
  Idempotency key in DynamoDB (source + file_type + file_date + s3_key). Before Spark writes to Kafka or the warehouse (Snowflake), we check; if already SUCCESS, we skip. In the warehouse we can also partition or key by file_date (or use MERGE in Snowflake) so overwrite is deterministic.

- **What if a row fails validation?**  
  We don’t fail the whole file. Valid rows go to curated S3 and Kafka; invalid rows go to S3 quarantine with the same file_date and a reject reason. Audit table has record_count and reject_count.

- **What AWS services did you use?**  
  S3 (landing, curated, quarantine), SNS, SQS, Lambda or Step Functions, EMR or Glue (Spark batch + Spark Streaming), Amazon MSK (Kafka), Snowflake (source of truth for analytics and API), Spring Boot API (queries Snowflake directly; JDBC + connection pooling), Angular app, DynamoDB (audit), CloudWatch, SNS for alerts.

Use this document to rehearse the **concrete** files, fields, reports, personas, and the **Spark + Kafka on AWS** flow so you can go deep in a 1-hour interview.
