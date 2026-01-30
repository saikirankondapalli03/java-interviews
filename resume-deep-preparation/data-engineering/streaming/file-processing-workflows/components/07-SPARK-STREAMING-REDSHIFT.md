# Component: Spark Streaming + Redshift Load

**Role in flow:** Spark Streaming (on EMR or Glue Streaming) consumes from MSK topics (`equilend.positions.curated`, `equilend.mtm_pnl.curated`), applies aggregations (e.g. by borrower_id, book_id) for reporting, and sinks to Redshift (or S3 Parquet for Athena).

---

## 1. Interview Pain Points & Nuances

### Micro-Batch (Structured Streaming)

- **How it works:** Spark Structured Streaming reads from Kafka in **micro-batches** (trigger interval, e.g. 1 minute). Each batch is a DataFrame; we can aggregate, join, then write to Redshift (or S3).
- **Checkpointing:** Spark writes checkpoint (e.g. to S3) with offsets and state. On restart, resume from checkpoint. **Interview:** "We use Spark Structured Streaming with Kafka source; we run micro-batches (e.g. every 1–5 minutes), aggregate for reporting, and write to Redshift. Checkpoint is on S3 so we can resume after failure."

### Aggregations for Reporting

- **Positions:** We may pass through row-level to Redshift (for daily_positions table) or pre-aggregate by borrower (exposure_by_borrower). Depends on whether we do aggregation in Spark or in Redshift (or in the Glue sync job that feeds the RDBMS).
- **P&L:** Similarly, row-level or pre-aggregate by book/desk. **Interview:** "We aggregate by borrower_id and book_id in Spark Streaming so that the Redshift tables are already at report grain (daily_positions, exposure_by_borrower, daily_pnl). That reduces Redshift compute and keeps the sync job to RDBMS simple."

### Sink to Redshift

- **Options:** (1) **JDBC** from Spark (write DataFrame to Redshift via JDBC driver). (2) **Redshift COPY from S3:** Spark writes to S3 (e.g. Parquet or CSV), then we run Redshift COPY (via Redshift Data API or a small Lambda/Step) to load from S3. (2) is more scalable for large volumes (COPY is bulk load). **Interview:** "We either write from Spark to Redshift via JDBC for moderate volume, or we write to S3 and then Redshift COPY from S3 for large volume. COPY is preferred for large batches."
- **Overwrite by file_date:** For idempotency we **overwrite** the partition (or delete + insert) by file_date and file_type so that duplicate consumption from Kafka doesn't duplicate rows. Redshift doesn't have "upsert" natively; we use staging table + DELETE from final WHERE file_date = X + INSERT from staging. Or use MERGE if available.

### Staging Table Pattern (Critical)

- **Flow:** Spark writes to a **staging** table (e.g. `equilend_positions_staging`). Then we run a **single SQL** (or Step Functions step): DELETE from final table WHERE file_date = X and file_type = Y; INSERT INTO final SELECT * FROM staging; (or MERGE). That way we never expose partial data to readers. **Interview:** "We write to a staging table, then in one transaction (or sequential step) we delete the target partition in the final table and insert from staging. So downstream always sees complete partitions, never partial."
- **Consistency:** Redshift doesn't support multi-statement transactions across COPY and DELETE the same way as an RDBMS; we design so that the "swap" (delete + insert) is as atomic as possible (e.g. single session, or use a view that points to staging until we flip to final).

### Glue Streaming vs EMR Spark Streaming

- **Glue Streaming:** Managed; uses Spark Structured Streaming under the hood. We define source (Kafka), transform, sink (e.g. Redshift connector or S3). **Interview:** "We use Glue Streaming for the consume-from-Kafka-and-load-Redshift step; it's managed and we don't run a long-lived EMR cluster."
- **EMR:** We run a long-running Spark Streaming job on EMR. More control over Spark config and version. **Interview:** "We could run Spark Streaming on EMR for more control; for this pipeline we use Glue Streaming for simplicity."

### Exactly-Once / At-Least-Once

- **Kafka:** We commit offsets after writing to Redshift. If we write then crash before commit, we reprocess — at-least-once. To get effectively-once we rely on **idempotent write** (overwrite by file_date) so reprocessing produces the same result.
- **Interview:** "We have at-least-once semantics from Kafka to Redshift; we make the write idempotent by overwriting the file_date partition so duplicate consumption doesn't corrupt data."

---

## 2. Data Engineering Metrics (Spark Streaming + Redshift)

| Metric | What to track | Why |
|--------|----------------|-----|
| **Consumer lag** | Kafka lag for our consumer group | Is streaming keeping up? |
| **Batch duration** | Time per micro-batch | Right-size resources. |
| **Redshift load time** | Per batch or per file_date | SLA; optimize COPY. |
| **Rows written to Redshift** | Per batch | Throughput; match to Kafka messages. |

---

## 3. Likely Interview Questions & Answers

- **Why Spark Streaming and not Lambda to consume Kafka?** Lambda can be triggered by Kafka (via event source mapping) but Lambda has concurrency and timeout limits; our aggregation and Redshift write can be heavy. Spark Streaming is designed for continuous processing and scale-out. **Interview:** "We use Spark Streaming (or Glue Streaming) because we need to aggregate and bulk-load to Redshift; Lambda is better for light per-message work."
- **How do you avoid duplicate rows in Redshift?** We overwrite by file_date (and file_type). Staging table + delete target partition + insert from staging. So duplicate consumption from Kafka still yields correct final state.
- **What if Redshift is down?** Spark Streaming will fail the write; we don't commit Kafka offset. On restart we reprocess the same batch. Retry and backoff; alert on repeated failures.

Use this when they ask "how does data get from Kafka to Redshift?" or "how do you handle exactly-once?"
