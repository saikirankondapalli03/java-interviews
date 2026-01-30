# Component: Amazon Redshift — Reporting Store (Source of Truth)

**Role in flow:** Redshift is the **analytical source of truth** for the full dataset. Spark Streaming (or Glue Streaming) loads curated data into Redshift (daily_positions, exposure_by_borrower, daily_pnl). Compliance and ad-hoc analytics query Redshift. A **Glue job** syncs reporting aggregates from Redshift into an **RDBMS** (PostgreSQL/Aurora); the **Spring Boot API** queries the RDBMS (not Redshift) for the Angular app.

---

## 1. Interview Pain Points & Nuances

### Why Redshift (and Not Only RDBMS)?

- **Redshift** = columnar, analytical warehouse. Best for **bulk scans**, aggregations, ad-hoc analytics, compliance queries. Scales for large datasets (millions/billions of rows).
- **RDBMS (Aurora/PostgreSQL)** = row-based, OLTP-style. Best for **indexed point lookups**, high-frequency API traffic, small result sets. Limited connections; not ideal for heavy analytical queries.
- **Our design:** Redshift holds the **full** curated dataset (source of truth). RDBMS holds **pre-aggregated** reporting tables for the API (Daily Positions, Exposure by Borrower, Daily P&L). Glue syncs Redshift → RDBMS. **Interview:** "Redshift is our analytical source of truth; we load from the pipeline and use it for compliance and ad-hoc analytics. We don't have the Spring Boot API hit Redshift directly — we sync aggregates to an RDBMS and the API queries that for low-latency UI."

### Why Not Spring Boot → Redshift Directly?

| Concern | Redshift direct (JDBC) | RDBMS as reporting serving layer |
|--------|------------------------|-----------------------------------|
| **Connection limits** | Redshift has limited concurrent connections per cluster; many API instances × pool size can exhaust them. | PostgreSQL/Aurora is built for many concurrent OLTP-style connections. |
| **Workload fit** | Redshift is columnar — best for bulk scans and analytics, not high-frequency, low-latency API traffic. | RDBMS is optimized for indexed point lookups and small result sets (exactly what "get this screen's data" needs). |
| **Latency** | Analytical queries can be hundreds of ms to seconds; acceptable for ad-hoc, not ideal for snappy UI. | Simple SELECTs on indexed tables give sub-second response for dashboard loads. |
| **Operational clarity** | Mixing "analytics" and "API serving" on the same cluster blurs SLAs and makes tuning harder. | Clear separation: Redshift = analytics; RDBMS = reporting serving layer. |

**Interview:** "We don't have the API hit Redshift directly. Redshift is our analytical source of truth with limited connections. A Glue job syncs reporting aggregates from Redshift into PostgreSQL/Aurora; the Spring Boot API queries that RDBMS via JDBC. So: Redshift → Glue sync → RDBMS → Spring Boot → Angular."

### COPY vs INSERT (Loading Data)

- **COPY:** Bulk load from S3 (Parquet, CSV) into Redshift. **Preferred** for large batches (millions of rows). Fast; uses Redshift's parallel load. **Interview:** "We load from Spark Streaming by writing to S3 and then Redshift COPY from S3, or we use JDBC from Spark for moderate volume. COPY is preferred for large batches."
- **INSERT (JDBC):** Spark (or Glue) can write via JDBC (INSERT INTO). Works for smaller batches; for large volume COPY is more efficient.
- **Redshift Data API:** Async submit query; poll for result. Useful for long-running loads or queries from serverless (Lambda) without holding a connection. **Interview:** "For async load from Lambda we could use Redshift Data API; for our pipeline Spark/Glue writes directly via JDBC or COPY from S3."

### Distribution Key and Sort Key

- **Distribution key:** How rows are distributed across nodes. Choose a column that is often used in JOINs or GROUP BY (e.g. `file_date`, `borrower_id`) so that co-located data is on the same node. **Interview:** "We distribute by file_date (or borrower_id for exposure queries) so that queries by date or borrower are co-located and fast."
- **Sort key:** Order of rows within a slice. Enables zone maps (min/max) for pruning. Choose columns used in WHERE (e.g. file_date, loan_id). **Interview:** "We sort by file_date and loan_id so that range queries on date and point lookups on loan_id are efficient."
- **Nuance:** Wrong distribution can cause **data skew** (one node has most of the data) and **broadcast** (large tables copied to every node). Right distribution = even spread and minimal data movement.

### Staging Table Then Final Table

- **Pattern:** Spark/Glue writes to **staging** table (e.g. `equilend_positions_staging`). Then we run DELETE from final WHERE file_date = X and file_type = Y; INSERT INTO final SELECT * FROM staging (or MERGE). So we never expose partial data. **Interview:** "We write to a staging table, then in one step we delete the target partition in the final table and insert from staging. Downstream never sees partial writes."
- **Consistency:** Redshift doesn't have multi-statement transactions across COPY and DELETE the same way as an RDBMS; we design so the swap is as atomic as possible (single session, or use a view that flips from staging to final).

### Connection Limits (Critical Nuance)

- **Redshift** has a **max concurrent connections** limit per cluster (e.g. 500 for large clusters). Each API instance × connection pool size consumes connections. Many API instances can exhaust the limit. **Interview:** "Redshift has limited concurrent connections; we don't want many API instances each holding a pool. So we sync to an RDBMS that's built for many connections and have the API query that."
- **Workload management (WLM):** Redshift can queue queries and assign memory/CPU per queue. If we mixed API traffic and analytics, we'd need WLM to protect long-running analytics from short API queries (or vice versa). Separating API (RDBMS) and analytics (Redshift) avoids that complexity.

### Schema and Tables

- **Tables:** e.g. `daily_positions` (row-level positions), `exposure_by_borrower` (aggregated by borrower_id), `daily_pnl` (P&L by book/desk). Same grain as the reports. Partitioned or keyed by file_date for overwrite and pruning.
- **Audit / lineage:** We can store file-level audit in Redshift (file_id, s3_uri, file_date, record_count, reject_count, processed_at) for compliance queries, or keep audit only in DynamoDB and query there.

### Security and Compliance

- **Encryption:** Redshift encrypts at rest (KMS or default). TLS in transit.
- **Access:** IAM or username/password. Least privilege: Glue/Spark role can load; analysts have read-only. No direct Redshift access from front end; only via API (RDBMS) or controlled query tool.
- **Column-level / table-level:** Redshift supports column-level security and row-level security (RLS) for sensitive data. **Interview:** "We use column-level or table-level access for sensitive reports so that only authorized personas see PII or certain columns."

---

## 2. Data Engineering Metrics (Redshift)

| Metric | What to track | Why |
|--------|----------------|-----|
| **Load time** | Per COPY or per batch | SLA; optimize. |
| **Rows loaded** | Per table per file_date | Completeness; match to source. |
| **Query queue depth** | WLM queue length | Backlog; scale or tune. |
| **Connection count** | Active connections | Avoid exhausting limit. |
| **Storage / compute** | Cluster size; utilization | Cost; right-size. |

---

## 3. Likely Interview Questions & Answers

- **Why use an RDBMS in addition to Redshift?** Redshift is analytical; limited connections; not ideal for high-frequency API traffic. RDBMS gives many connections, indexed lookups, sub-second response for "get this screen's data." We use RDBMS only for the pre-aggregated reporting tables the API needs — not as the source of truth.
- **Do you recommend a Glue job to dump Redshift to RDBMS?** Yes. A Glue job (or scheduled Spark job) that runs after the main pipeline has loaded Redshift is the right way to sync reporting aggregates (daily_positions, exposure_by_borrower, daily_pnl) into PostgreSQL/Aurora. The API then reads from the RDBMS. Keeps Redshift for analytics and gives the UI a fast, connection-friendly serving layer.
- **What if Redshift is slow or down?** Spark Streaming (or Glue) will fail the write; we don't commit Kafka offset; we retry. We monitor load time and queue depth; if Redshift is consistently slow we scale the cluster or tune WLM. For API, the RDBMS is separate — so API availability doesn't depend on Redshift being up for each request (only on the sync having run).
- **How do you avoid duplicate rows in Redshift when consuming from Kafka at-least-once?** We overwrite by file_date (and file_type). Staging table + DELETE target partition + INSERT from staging. So duplicate consumption from Kafka still yields correct final state.

Use this when they ask "why Redshift?" or "why not have the API query Redshift?" or "how do you load and structure Redshift?"
