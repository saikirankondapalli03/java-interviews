# Data Engineering Metrics & Interview Pain Points — Master Reference

**Purpose:** What interviewers (and Solutions Architects) care about in file processing workflows: metrics, SLAs, failure modes, and nuances. Use this as the checklist when discussing each component.

**Believability:** Interviewers may have worked on Equilend or similar. The Equilend scenario (10K–2M rows/file, T+1 SLA, file drops into S3) is realistic for large agency-lending books—so the pipeline (Spark, SQS, idempotency, audit) is justified, not overkill. Establish that the business problem is real before defending the design.

---

## 1. Data Engineering Metrics (What Interviewers Look At)

| Metric | Definition | Where It Matters | Target (Equilend) |
|--------|------------|------------------|-------------------|
| **Freshness / Latency** | Time from source event (file drop) to data available for consumption. | End-to-end pipeline; each stage. | T+1 morning (e.g. 6 AM ET) — file drops EOD, reports ready by next morning. |
| **Throughput** | Records or files processed per unit time. | S3 events, SQS, Glue/Spark, Kafka, Snowflake load. | 10K–2M rows/file; 1–2 files/day per type; pipeline must complete within SLA window. |
| **Completeness** | % of expected data that arrived and was processed. | File receipt (did we get the file?), row-level (record_count vs expected). | 100% of expected files; reject_count in audit for partial. |
| **Correctness** | Data matches business rules; no silent corruption. | Validation in Spark; schema; idempotency. | Valid rows to curated; invalid to quarantine; audit has record_count, reject_count. |
| **Idempotency** | Same input processed twice → same output; no duplicates. | Orchestrator + DynamoDB; Spark write strategy. | Idempotency key (source + file_type + file_date + s3_key); skip or overwrite. |
| **Availability / Reliability** | Pipeline runs when files arrive; retries and DLQ. | SQS retries, visibility timeout; Glue/EMR retries; DLQ + alerts. | At-least-once processing; DLQ for poison messages; alarms on DLQ depth. |
| **Observability** | Can you see what’s running, what failed, and why? | Logs, metrics, audit table, lineage. | CloudWatch logs/metrics; DynamoDB audit (status, record_count, reject_count, job_id); SNS alerts. |
| **Cost** | DPU/min (Glue), cluster hours (EMR), S3 storage, Snowflake compute. | Glue DPUs, EMR node count, S3 lifecycle, Snowflake warehouse concurrency. | Right-size Glue; transient EMR or serverless where possible; lifecycle on landing/curated. |
| **Schema evolution** | New columns or file types without breaking pipeline. | Config-driven schema (Glue/Spark); validation rules. | Schema in S3/Parameter Store; file type from prefix; backward-compatible adds. |

---

## 2. Cross-Cutting Pain Points (Interview Grilling)

- **Why not Lambda for the file?** Lambda has 15 min max timeout, 10 GB memory; our files can be millions of rows. Validation and transforms need distributed processing — Spark (Glue/EMR) fits. We also publish to Kafka; Lambda would need to call MSK, and we’d still need something to run the heavy lift.
- **Exactly-once vs at-least-once?** File workflow: we aim for **effectively once** via idempotency (DynamoDB check + skip or overwrite by file_date). Kafka/Spark can be at-least-once or exactly-once depending on config; we key by file_date so duplicate consumption still yields same result after overwrite.
- **What if the file is late or missing?** Monitoring: expected file count per day (e.g. 2: positions + P&L). CloudWatch metric or scheduled check: “files received today” vs expected. Alert if missing; optionally trigger manual upload or re-drop. No automatic “wait forever” — we have SLA by T+1 morning.
- **Ordering:** We don’t rely on strict ordering across files. We do need **per-file** consistency: all rows of one file committed together (staging then commit). For positions vs P&L, we may have ordering requirements (e.g. P&L after positions) — enforce via pipeline order or dependency in Step Functions.
- **Backpressure:** SQS decouples “file arrived” from “process.” If Glue is slow, messages accumulate in SQS; we don’t lose events. Visibility timeout must be > max job duration so we don’t reprocess same file while job is still running.
- **Schema drift / bad data:** Validation in Spark; invalid rows → quarantine. Schema in config so we can add optional columns without redeploying code. Reject reason in quarantine for debugging.

---

## 3. Component Index (Where to Go Deep)

| Component | Doc | Key Interview Angles |
|-----------|-----|----------------------|
| S3 (landing, curated, quarantine) | `01-S3.md` | Event types, consistency, prefix design, lifecycle, multipart |
| SNS + SQS (eventing) | `02-SNS-SQS-EVENTING.md` | At-least-once, visibility timeout, DLQ, message attributes |
| Orchestrator (Lambda / Step Functions) | `03-ORCHESTRATION-LAMBDA-STEP-FUNCTIONS.md` | When Lambda vs Step Functions; idempotency check; invoking Glue |
| DynamoDB (audit / idempotency) | `04-DYNAMODB-AUDIT-IDEMPOTENCY.md` | Key design, conditional writes, status transitions |
| Glue + PySpark (batch) | `05-GLUE-PYSPARK-BATCH.md` | Job bookmarks vs our pattern, DPUs, config, validation, writing S3 + Kafka |
| MSK (Kafka) | `06-MSK-KAFKA.md` | Topics, partitioning, producer/consumer semantics, scaling |
| Spark Streaming + Snowflake load | `07-SPARK-STREAMING-SNOWFLAKE.md` | Micro-batch, Snowflake sink, staging table + MERGE |
| Snowflake | `08-SNOWFLAKE.md` | COPY INTO, clustering, MERGE; **API queries Snowflake directly** |
| Optional: Snowflake → RDBMS sync | `09-GLUE-SYNC-RDBMS.md` | When you'd add it; not in our main flow |
| Observability | `10-OBSERVABILITY-CLOUDWATCH.md` | Metrics, alarms, logs, lineage |

---

## 4. One-Page “Metrics by Stage” (Memorize for Interview)

| Stage | Primary metrics | Failure mode | Mitigation |
|-------|-----------------|--------------|------------|
| **S3 landing** | Object count, size; event delivery | Late/missing file | Alert on expected count; SLA monitoring |
| **SNS → SQS** | Messages published/received; queue depth | Message loss (rare) | SQS is durable; visibility timeout > job duration |
| **Orchestrator** | Invocations; DynamoDB read/write | Double submit | Idempotency key; conditional write “only if not SUCCESS” |
| **Glue/Spark** | Job duration; records read/written; reject count | Job failure; partial write | Retry; quarantine; audit status PARTIAL/FAILED |
| **Kafka (MSK)** | Produce/consume lag; throughput | Lag growth | Scale consumers; check partition key |
| **Snowflake** | Load time; COPY INTO rows; queue depth | Load failure; connection exhaustion | Staging table; MERGE; Snowpipe async if needed |
| **Audit (DynamoDB)** | Item count; read/write capacity | Throttling | On-demand or enough RCU/WCU for check + update per file |
| **End-to-end** | Freshness (file drop → report available) | SLA breach | Track per file_date; alert if T+1 6 AM passed and data missing |

Use this master doc to anchor your answers; then go deep per component in the numbered files.
