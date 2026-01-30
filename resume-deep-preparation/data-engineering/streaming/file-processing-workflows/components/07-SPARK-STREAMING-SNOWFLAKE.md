# Component: Spark Streaming + Snowflake Load

**Role in flow:** Spark Streaming (on EMR or Glue Streaming) consumes from MSK topics (`equilend.positions.curated`, `equilend.mtm_pnl.curated`), applies aggregations (e.g. by borrower_id, book_id) for reporting, and sinks to **Snowflake** (or S3 Parquet for Athena). Use Snowflake Spark connector or S3 → Snowflake COPY INTO; staging + overwrite-by-file_date with MERGE.

---

## 1. Interview Pain Points & Nuances

### Micro-Batch (Structured Streaming)

- **How it works:** Spark Structured Streaming reads from Kafka in **micro-batches** (trigger interval, e.g. 1 minute). Each batch is a DataFrame; we can aggregate, join, then write to Snowflake (or S3).
- **Checkpointing:** Spark writes checkpoint (e.g. to S3) with offsets and state. On restart, resume from checkpoint. **Interview:** "We use Spark Structured Streaming with Kafka source; we run micro-batches (e.g. every 1–5 minutes), aggregate for reporting, and write to Snowflake. Checkpoint is on S3 so we can resume after failure."

### Aggregations for Reporting

- **Positions:** We may pass through row-level to Snowflake (for daily_positions table) or pre-aggregate by borrower (exposure_by_borrower). Depends on whether we do aggregation in Spark or in Snowflake.
- **P&L:** Similarly, row-level or pre-aggregate by book/desk. **Interview:** "We aggregate by borrower_id and book_id in Spark Streaming so that the Snowflake tables are already at report grain (daily_positions, exposure_by_borrower, daily_pnl). That reduces Snowflake compute and keeps the API queries simple."

### Sink to Snowflake

- **Snowflake Spark connector:** Write DataFrame to Snowflake via JDBC or native connector (internal stage or S3).
- **Snowflake COPY INTO from S3:** Spark writes to S3, then Snowflake COPY INTO from external stage. Preferred for large volumes (bulk load).
- **MERGE:** Snowflake has strong MERGE support for idempotent overwrite by file_date and file_type. Staging table + MERGE into final so duplicate consumption from Kafka still yields correct final state.
- **Interview:** "We either write from Spark to Snowflake via the Snowflake Spark connector for moderate volume, or we write to S3 and then Snowflake COPY INTO from S3 for large volume. We use staging table + MERGE by file_date for idempotency."

### Staging Table Pattern (Critical)

- **Flow:** Spark writes to a **staging** table (e.g. `equilend_positions_staging`). Then we run **MERGE** into the final table by file_date and file_type (or DELETE target partition + INSERT from staging). That way we never expose partial data to readers.
- **Interview:** "We write to a staging table, then MERGE into the final table by file_date and file_type. So downstream always sees complete partitions, never partial."

### Glue Streaming vs EMR Spark Streaming

- **Glue Streaming:** Managed; uses Spark Structured Streaming under the hood. We define source (Kafka), transform, sink (Snowflake connector or S3). **Interview:** "We use Glue Streaming for the consume-from-Kafka-and-load-Snowflake step; it's managed and we don't run a long-lived EMR cluster."
- **EMR:** We run a long-running Spark Streaming job on EMR. More control over Spark config and version. **Interview:** "We could run Spark Streaming on EMR for more control; for this pipeline we use Glue Streaming for simplicity."

### Exactly-Once / At-Least-Once

- **Kafka:** We commit offsets after writing to Snowflake. If we write then crash before commit, we reprocess — at-least-once. To get effectively-once we rely on **idempotent write** (overwrite by file_date via MERGE) so reprocessing produces the same result.
- **Interview:** "We have at-least-once semantics from Kafka to Snowflake; we make the write idempotent by MERGE (or overwrite) on file_date so duplicate consumption doesn't corrupt data."

---

## 2. Data Engineering Metrics (Spark Streaming + Snowflake)

| Metric | What to track | Why |
|--------|----------------|-----|
| **Consumer lag** | Kafka lag for our consumer group | Is streaming keeping up? |
| **Batch duration** | Time per micro-batch | Right-size resources. |
| **Snowflake load time** | Per batch or per file_date | SLA; optimize COPY INTO. |
| **Rows written to Snowflake** | Per batch | Throughput; match to Kafka messages. |

---

## 3. Likely Interview Questions & Answers

- **Why Spark Streaming and not Lambda to consume Kafka?** Lambda has concurrency and timeout limits; our aggregation and Snowflake write can be heavy. Spark Streaming is designed for continuous processing and scale-out. **Interview:** "We use Spark Streaming (or Glue Streaming) because we need to aggregate and bulk-load to Snowflake; Lambda is better for light per-message work."
- **How do you avoid duplicate rows in Snowflake?** We overwrite by file_date (and file_type). Staging table + MERGE into final by file_date/file_type. So duplicate consumption from Kafka still yields correct final state.
- **What if Snowflake is down?** Spark Streaming will fail the write; we don't commit Kafka offset. On restart we reprocess the same batch. Retry and backoff; alert on repeated failures.

Use this when they ask "how does data get from Kafka to Snowflake?" or "how do you handle exactly-once?"
