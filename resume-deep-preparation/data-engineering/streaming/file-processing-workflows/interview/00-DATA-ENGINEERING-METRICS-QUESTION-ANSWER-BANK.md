# Component 00: Data Engineering Metrics & Pain Points — Question & Answer Bank

**Reference:** `components/00-DATA-ENGINEERING-METRICS-AND-PAINPOINTS.md`  
**Interview coverage:** Intro, architecture, technical depth, scenarios, follow-ups, evaluation. Leave no stone unturned.

---

## 1. Intro & Context (Opening)

### Q1.1 What metrics do interviewers and Solutions Architects care about most in file processing workflows?

**Answer:** They care about: **freshness/latency** (time from file drop to data available), **throughput** (records/files per unit time), **completeness** (expected vs arrived), **correctness** (valid vs invalid, no silent corruption), **idempotency** (same input → same output; no duplicates), **availability/reliability** (retries, DLQ, pipeline runs when files arrive), **observability** (logs, metrics, audit, lineage), **cost** (DPU-min, storage, compute), and **schema evolution** (new columns/file types without breaking the pipeline). For Equilend-style flows: T+1 SLA, 10K–2M rows/file, 100% expected files, valid rows to curated and invalid to quarantine, idempotency key per file, DLQ and alarms.

---

### Q1.2 How do you establish believability when discussing an Equilend-style file workflow in an interview?

**Answer:** State that the business scenario is realistic: 10K–2M rows/file, T+1 SLA, file drops into S3 at EOD, reports ready by next morning. Large agency-lending books often have this pattern. The pipeline (Spark, SQS, idempotency, audit) is justified, not overkill—establish the business problem first, then defend the design.

---

## 2. Architecture & Ownership

### Q2.1 Walk me through the “metrics by stage” for a file processing pipeline. What is the primary metric and failure mode at each stage?

**Answer:**

| Stage | Primary metrics | Failure mode | Mitigation |
|-------|-----------------|--------------|------------|
| **S3 landing** | Object count, size; event delivery | Late/missing file | Alert on expected count; SLA monitoring |
| **SNS → SQS** | Messages published/received; queue depth | Message loss (rare) | SQS durable; visibility timeout > job duration |
| **Orchestrator** | Invocations; DynamoDB read/write | Double submit | Idempotency key; conditional write “only if not SUCCESS” |
| **Glue/Spark** | Job duration; records read/written; reject count | Job failure; partial write | Retry; quarantine; audit status PARTIAL/FAILED |
| **Kafka (MSK)** | Produce/consume lag; throughput | Lag growth | Scale consumers; check partition key |
| **Snowflake** | Load time; COPY INTO rows; queue depth | Load failure; connection exhaustion | Staging table; MERGE; Snowpipe async if needed |
| **Audit (DynamoDB)** | Item count; read/write capacity | Throttling | On-demand or enough RCU/WCU per file |
| **End-to-end** | Freshness (file drop → report available) | SLA breach | Track per file_date; alert if T+1 6 AM passed and data missing |

---

### Q2.2 Why not use Lambda to process the file end-to-end?

**Answer:** Lambda has 15 min max timeout and 10 GB memory; our files can be millions of rows. Validation and transforms need distributed processing—Spark (Glue/EMR) fits. We also publish to Kafka; Lambda would need to call MSK, and we’d still need something to run the heavy lift. Lambda is the **orchestrator** (read SQS, check DynamoDB, start Glue), not the **processor**.

---

### Q2.3 Exactly-once vs at-least-once in a file workflow—what do we aim for and how?

**Answer:** We aim for **effectively once** via idempotency: DynamoDB check + skip or overwrite by file_date. Kafka/Spark can be at-least-once or exactly-once depending on config; we key by file_date so duplicate consumption still yields the same result after overwrite. No strict exactly-once across all components; idempotent design gives correct end state.

---

## 3. Technical Depth (Pain Points)

### Q3.1 What if the file is late or missing?

**Answer:** Monitoring: expected file count per day (e.g. 2: positions + P&L). CloudWatch metric or scheduled check: “files received today” vs expected. Alert if missing; optionally trigger manual upload or re-drop. No automatic “wait forever”—we have SLA by T+1 morning.

---

### Q3.2 Do we need strict ordering across files? Within a file?

**Answer:** We don’t rely on strict ordering **across** files. We need **per-file** consistency: all rows of one file committed together (staging then commit). For positions vs P&L we may have ordering (e.g. P&L after positions)—enforce via pipeline order or dependency in Step Functions.

---

### Q3.3 How do you handle backpressure when Glue is slow?

**Answer:** SQS decouples “file arrived” from “process.” If Glue is slow, messages accumulate in SQS; we don’t lose events. Visibility timeout must be > max job duration so we don’t reprocess the same file while the job is still running. Monitor queue depth; scale Glue or add workers if backlog grows.

---

### Q3.4 How do you handle schema drift and bad data?

**Answer:** Validation in Spark; invalid rows → quarantine. Schema in config (S3/Parameter Store) so we can add optional columns without redeploying code. Reject reason in quarantine for debugging. We don’t fail the whole file—record_count and reject_count in audit.

---

## 4. Scenario & Design

### Q4.1 “You need to ingest 10TB/day from S3 into a curated layer. SLA is 2 hours. What would you design?”

**Answer:** Partition strategy (by date/source); parallelism (multiple Glue workers or EMR); Glue vs Spark (Glue for managed, EMR for custom); file format (Parquet); compression (Snappy); event-driven or scheduled trigger; idempotency by partition; monitor job duration and DPU-minutes; alarm on SLA breach.

---

### Q4.2 How would you add a new file type (e.g. a third Equilend report) without breaking the pipeline?

**Answer:** Config-driven: add new prefix (e.g. `collateral/`) and file type in config; schema and validation rules in Parameter Store or S3; Glue reads config at runtime. Idempotency key includes file_type. No code deploy for new optional columns if schema is backward-compatible.

---

## 5. Follow-ups & Seniority

### Q5.1 How do you decide between star schema, snowflake, and wide tables in analytics for this pipeline?

**Answer:** Star for BI (daily_positions, exposure_by_borrower, daily_pnl as fact/dim or flat report tables). Snowflake when dimension reuse is high. Wide tables for performance or ML features. For Equilend we have pre-aggregated report grain in Snowflake; API does simple SELECTs.

---

### Q5.2 Have you handled slowly changing dimensions? Which type and why?

**Answer:** For file-based T+1 we typically overwrite by file_date (Type 1). If we needed history we’d use Type 2 (effective date, current flag) in the curated layer or in Snowflake; document the choice (e.g. “we use Type 1 for daily snapshot; Type 2 for borrower attributes if required”).

---

### Q5.3 How do you ensure data quality in production pipelines?

**Answer:** Checks: nulls, duplicates, schema drift, business rules (e.g. quantity > 0, settle_date >= trade_date). Reprocessing strategy: idempotent overwrite by file_date. Idempotency: DynamoDB + conditional writes. Alerts: CloudWatch on reject count, quarantine growth, job failure, DLQ.

---

## 6. Evaluation Cheat Sheet (For This Component)

| Area | What good looks like | Red flag |
|------|----------------------|----------|
| **Metrics** | Names freshness, completeness, correctness, idempotency, availability; ties to stages | Vague “we monitor things” |
| **Tradeoffs** | Explains Lambda vs Spark, at-least-once vs effectively-once, ordering vs throughput | Tool dumping without reasoning |
| **SLA** | Clear T+1, expected file count, alert on missing/late | No mention of SLA or alerts |
| **Cross-cutting** | Backpressure, schema drift, late file, replay | Only talks about one component |

Use this bank with the component doc to rehearse; then go deep per component in the numbered files.
