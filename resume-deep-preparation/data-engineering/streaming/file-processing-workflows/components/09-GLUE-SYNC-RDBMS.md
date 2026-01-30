# Component: Glue Sync Job (Redshift → RDBMS)

**Role in flow:** After the main Equilend pipeline has loaded Redshift (Spark Streaming has written daily_positions, exposure_by_borrower, daily_pnl), a **Glue job** (or scheduled Spark job) syncs those **reporting aggregates** from Redshift into an **RDBMS** (PostgreSQL or Aurora). The Spring Boot API then queries the RDBMS (not Redshift) for the Angular app.

---

## 1. Interview Pain Points & Nuances

### When Does the Sync Run?

- **Trigger:** (1) **Scheduled** (e.g. after T+1 load window, e.g. 6 AM ET). (2) **Event-driven** (e.g. Step Functions or EventBridge rule when Spark Streaming job completes). (3) **On-demand** for replay.
- **Interview:** "We run the sync job on a schedule after the main pipeline has loaded Redshift (e.g. 6:30 AM ET so data is in RDBMS by 7 AM for the UI). We could also trigger it when the Spark Streaming job completes so the RDBMS is updated as soon as Redshift is ready."

### Full vs Incremental Sync

- **Full sync:** Each run reads the full reporting tables from Redshift (or the relevant partition, e.g. last 7 days) and overwrites the corresponding rows in the RDBMS. Simple; ensures consistency. **Interview:** "We do a full overwrite of the reporting tables (or by file_date partition) so that the RDBMS always matches Redshift for the report grain. Simple and correct."
- **Incremental:** Only sync new or changed rows (e.g. by file_date > last_synced). More efficient for large tables; requires watermark or last_synced state. For "three report tables, one sync per day" full is usually fine. **Interview:** "For our use case we sync the full report grain (e.g. last N days or full table) each run. If we had very large tables we could do incremental by file_date."

### What Gets Synced?

- **Tables:** `daily_positions`, `exposure_by_borrower`, `daily_pnl` (or whatever grain the Redshift tables have). Same schema in RDBMS so the API can query by file_date, borrower_id, book_id, etc.
- **Source:** Redshift (read via JDBC from Glue/Spark) or **same S3 curated Parquet** that Redshift is loaded from (so we don't double-read Redshift). **Interview:** "We read from Redshift via JDBC in the Glue job, or we read from the same S3 Parquet that Spark Streaming loads to Redshift — that way we don't add load to Redshift. We write to PostgreSQL/Aurora tables with the same schema as the report screens need."

### Consistency: Redshift vs RDBMS

- **Eventual consistency:** RDBMS is updated after Redshift. There's a window (e.g. 30 min after T+1 load) where Redshift has data but RDBMS might not yet. UI shows "data as of last sync." **Interview:** "The RDBMS is eventually consistent with Redshift; we run the sync after the pipeline load so the UI has data by 7 AM. If someone queries Redshift directly they might see data a bit earlier."
- **Overwrite strategy:** We overwrite the RDBMS tables (or partition by file_date) so each sync run replaces the target data. No merge conflicts; deterministic state.

### Glue Job Design

- **Read:** Redshift via JDBC (Glue has Redshift connector) or S3 Parquet. Use partition pruning (file_date) if reading from S3.
- **Write:** RDBMS (Aurora/PostgreSQL) via JDBC. Use batch INSERT or COPY (PostgreSQL COPY) for speed. Truncate or DELETE target partition before insert so we don't duplicate.
- **DPUs:** Same as batch Glue; size for read + write volume. Usually one short job per day.
- **Interview:** "The sync job reads from Redshift (or S3) the reporting aggregates, then writes to Aurora/PostgreSQL via JDBC. We overwrite by file_date so each run is idempotent. It runs once per day after the main pipeline."

### Failure Handling

- **If sync fails:** RDBMS has stale data. Alert ops; retry. API continues to serve last good sync. **Interview:** "If the sync job fails we alert; the UI keeps showing last successful sync. We retry the job or fix the cause and re-run."
- **If Redshift is down:** Sync can't read; job fails. Same as above; retry when Redshift is back.

---

## 2. Data Engineering Metrics (Glue Sync)

| Metric | What to track | Why |
|--------|----------------|-----|
| **Sync job duration** | Time per run | SLA; right-size. |
| **Rows synced** | Per table | Completeness; match to Redshift. |
| **Sync success / failure** | Job status | RDBMS freshness; alert on failure. |
| **RDBMS freshness** | Last successful sync timestamp | "Data as of" for UI. |

---

## 3. Likely Interview Questions & Answers

- **Why not have the API query Redshift directly?** Redshift has limited concurrent connections and is optimized for analytical workloads, not high-frequency API traffic. The RDBMS gives many connections, indexed lookups, sub-second response. We sync so the API has a fast, connection-friendly serving layer.
- **How often does the sync run?** Once per day after the main pipeline has loaded Redshift (e.g. 6:30 AM). We could run more frequently if we needed fresher data in the UI.
- **What if the sync is slow?** We size the Glue job (DPUs, parallelism) so it finishes within a window (e.g. 30 min). If it's slow we scale or optimize the read/write (batch size, COPY vs INSERT).
- **Do you need an RDBMS? Why not just S3 or Athena?** RDBMS gives flexible querying (filters, pagination, "show me borrower X across dates") without pre-generating every report file. Athena is great for ad-hoc SQL on S3 but not for high-frequency, low-latency API traffic (query latency and concurrency limits). So we don't have the API call Athena for each request.

Use this when they ask "how does the API get its data?" or "why sync Redshift to RDBMS?"
