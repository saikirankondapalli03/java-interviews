# Component: Snowflake — Reporting Store (Source of Truth)

**Role in flow:** Snowflake is the **analytical source of truth** for the full dataset. Spark Streaming (or Glue Streaming) loads curated data into Snowflake (daily_positions, exposure_by_borrower, daily_pnl). The **Spring Boot API queries Snowflake directly** (JDBC + connection pooling) to serve the Angular app. Compliance and ad-hoc analytics also query Snowflake.

**Our design:** No Glue sync to an RDBMS. For internal, T+1 reporting with moderate concurrency, API → Snowflake is sufficient. Use a small dedicated warehouse (or serverless) and connection pooling.

---

## 1. Interview Pain Points & Nuances

### Why Snowflake?

- **Snowflake** = cloud-native, columnar analytical warehouse. Best for **bulk scans**, aggregations, ad-hoc analytics, compliance queries. Scales for large datasets (millions/billions of rows); separation of compute and storage.
- **Our design:** Snowflake holds the **full** curated dataset. The API queries Snowflake directly for the three report screens (Daily Positions, Exposure by Borrower, Daily P&L). One source of truth; no sync job. **Interview:** "Snowflake is our reporting source of truth. We load from the pipeline and the Spring Boot API queries Snowflake directly via JDBC with connection pooling. For our scale we don't need a separate RDBMS sync."

### API → Snowflake Directly (How We Do It)

- **Connection pooling:** Limit pool size per API instance so total connections stay within Snowflake's limit for the warehouse. Use a **small dedicated warehouse** (or serverless) for the API workload so we don't mix heavy analytics with UI traffic.
- **Queries:** Simple SELECTs by file_date, borrower_id, book_id — the tables are already at report grain (daily_positions, exposure_by_borrower, daily_pnl). Clustering by file_date keeps these fast.
- **Interview:** "The API hits Snowflake with a small connection pool and a dedicated warehouse. Our queries are simple filters on pre-aggregated tables, so latency is acceptable. If we ever hit connection or cost limits we'd add a sync to an RDBMS (see optional doc 09)."

### COPY INTO vs INSERT (Loading Data)

- **COPY INTO:** Bulk load from S3 (or internal stage) into Snowflake. **Preferred** for large batches (millions of rows). Use an **external stage** (S3) or **internal stage**; Spark can write to S3 then Snowflake COPY INTO, or use the **Snowflake Spark connector** (writes via internal stage or S3). **Interview:** "We load from Spark Streaming by writing to S3 and then Snowflake COPY INTO from S3, or we use the Snowflake Spark connector. COPY INTO is preferred for large batches."
- **INSERT (JDBC / Spark connector):** Spark (or Glue) can write via JDBC or the Snowflake connector (INSERT or merge). Works for smaller batches; for large volume COPY INTO from S3 is more efficient.
- **Snowpipe:** Optional for continuous, event-driven load from S3; for our T+1 batch workflow, scheduled COPY INTO or Spark → S3 → COPY is typical.

### Clustering and Micro-Partitions

- **Micro-partitions:** Snowflake automatically partitions data into micro-partitions. **Clustering keys** (e.g. `file_date`, `borrower_id`) optimize query pruning. **Interview:** "We cluster by file_date (or borrower_id for exposure queries) so that queries by date or borrower are efficient."
- **No distribution key:** Snowflake manages distribution internally; we focus on clustering for common filters and JOINs.

### Staging Table + MERGE (Critical)

- **Pattern:** Spark/Glue writes to a **staging** table (e.g. `equilend_positions_staging`). Then we run **MERGE** into the final table on `file_date` + `file_type` (and optionally row keys). Snowflake has **excellent MERGE support**. So we never expose partial data. **Interview:** "We write to a staging table, then MERGE into the final table by file_date and file_type. So downstream never sees partial writes; MERGE gives us idempotent upsert."
- **Idempotency:** Overwrite by file_date so duplicate consumption from Kafka still yields correct final state.

### Schema and Tables

- **Tables:** e.g. `daily_positions`, `exposure_by_borrower`, `daily_pnl`. Same grain as the reports. Clustered by file_date for overwrite and pruning.
- **Audit / lineage:** We can store file-level audit in Snowflake (file_id, s3_uri, file_date, record_count, reject_count, processed_at) for compliance queries, or keep audit only in DynamoDB and query there.

### Security and Compliance

- **Encryption:** Snowflake encrypts at rest (AES-256) and in transit (TLS). Key management (BYOK) optional.
- **Access:** RBAC (roles and privileges); integration with Okta, Azure AD, etc. Least privilege: Glue/Spark role can load; API role can read reporting tables; analysts have read-only. No direct Snowflake access from front end; only via API. **Interview:** "We use masking or row access policies for sensitive reports so that only authorized personas see PII or certain columns."

---

## 2. Data Engineering Metrics (Snowflake)

| Metric | What to track | Why |
|--------|----------------|-----|
| **Load time** | Per COPY INTO or per batch | SLA; optimize. |
| **Rows loaded** | Per table per file_date | Completeness; match to source. |
| **Query queue / concurrency** | Warehouse utilization | Backlog; scale or tune. |
| **Connection count** | Active connections per warehouse | Avoid exhausting limit (API pool size). |
| **Storage / compute** | Warehouse size; credits used | Cost; right-size. |

---

## 3. Likely Interview Questions & Answers

- **Why does the API query Snowflake directly?** For our use case (internal reporting, T+1, a few screens, moderate concurrency) we don't need a separate RDBMS. We use connection pooling and a small dedicated warehouse. One source of truth; fewer moving parts. If we scaled up we'd consider a sync to an RDBMS (see optional doc 09).
- **What about connection limits?** We size the connection pool and use a dedicated warehouse for the API. Snowflake multi-cluster warehouses can scale concurrency. We monitor connection count and would add a sync only if we hit limits.
- **What if Snowflake is slow or down?** Spark Streaming will fail the write; we don't commit Kafka offset; we retry. For the API, we'd see higher latency or errors; retry and alert. We don't have a separate RDBMS to fall back to — that's the tradeoff for simplicity.
- **How do you avoid duplicate rows in Snowflake when consuming from Kafka at-least-once?** We overwrite by file_date (and file_type). Staging table + MERGE. So duplicate consumption from Kafka still yields correct final state.

Use this when they ask "why Snowflake?" or "how does the API get its data?" — **API queries Snowflake directly; no Glue sync for our flow.**
