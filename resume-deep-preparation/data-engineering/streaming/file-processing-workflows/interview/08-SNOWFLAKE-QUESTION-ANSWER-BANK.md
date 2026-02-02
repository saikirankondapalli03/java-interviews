# Component 08: Snowflake — Question & Answer Bank

**Reference:** `components/08-SNOWFLAKE.md`  
**Interview coverage:** Intro, architecture, technical depth, scenarios, follow-ups, evaluation.

---

## 1. Intro & Context (Opening)

### Q1.1 What is the role of Snowflake in the file processing flow?

**Answer:** Snowflake is the **analytical source of truth** for the full dataset. Spark Streaming (or Glue Streaming) loads curated data into Snowflake (daily_positions, exposure_by_borrower, daily_pnl). The **Spring Boot API queries Snowflake directly** (JDBC + connection pooling) to serve the Angular app. Compliance and ad-hoc analytics also query Snowflake. No Glue sync to an RDBMS; for internal T+1 reporting with moderate concurrency, API → Snowflake is sufficient.

---

### Q1.2 Why Snowflake?

**Answer:** Snowflake = cloud-native, columnar analytical warehouse. Best for **bulk scans**, aggregations, ad-hoc analytics, compliance queries. Scales for large datasets (millions/billions of rows); separation of compute and storage. **Our design:** Snowflake holds the **full** curated dataset. The API queries Snowflake directly for the three report screens. One source of truth; no sync job. **Interview:** “Snowflake is our reporting source of truth. We load from the pipeline and the Spring Boot API queries Snowflake directly via JDBC with connection pooling. For our scale we don’t need a separate RDBMS sync.”

---

## 2. Architecture & Ownership

### Q2.1 How does the API query Snowflake directly? Connection pooling?

**Answer:** **Connection pooling:** Limit pool size per API instance so total connections stay within Snowflake’s limit for the warehouse. Use a **small dedicated warehouse** (or serverless) for the API workload so we don’t mix heavy analytics with UI traffic. **Queries:** Simple SELECTs by file_date, borrower_id, book_id—the tables are already at report grain. Clustering by file_date keeps these fast. **Interview:** “The API hits Snowflake with a small connection pool and a dedicated warehouse. Our queries are simple filters on pre-aggregated tables, so latency is acceptable. If we ever hit connection or cost limits we’d add a sync to an RDBMS (see optional doc 09).”

---

### Q2.2 COPY INTO vs INSERT (JDBC / Spark connector)—when do you use which?

**Answer:** **COPY INTO:** Bulk load from S3 (or internal stage) into Snowflake. **Preferred** for large batches (millions of rows). Use an **external stage** (S3) or **internal stage**; Spark can write to S3 then Snowflake COPY INTO, or use the **Snowflake Spark connector** (writes via internal stage or S3). **INSERT (JDBC / Spark connector):** Works for smaller batches; for large volume COPY INTO from S3 is more efficient. **Snowpipe:** Optional for continuous, event-driven load from S3; for T+1 batch workflow, scheduled COPY INTO or Spark → S3 → COPY is typical. **Interview:** “We load from Spark Streaming by writing to S3 and then Snowflake COPY INTO from S3, or we use the Snowflake Spark connector. COPY INTO is preferred for large batches.”

---

### Q2.3 Clustering and micro-partitions—how do you optimize queries?

**Answer:** **Micro-partitions:** Snowflake automatically partitions data into micro-partitions. **Clustering keys** (e.g. `file_date`, `borrower_id`) optimize query pruning. **Interview:** “We cluster by file_date (or borrower_id for exposure queries) so that queries by date or borrower are efficient.” No distribution key; Snowflake manages distribution internally; we focus on clustering for common filters and JOINs.

---

## 3. Technical Depth (Pain Points)

### Q3.1 Staging table + MERGE—why and how?

**Answer:** **Pattern:** Spark/Glue writes to a **staging** table (e.g. `equilend_positions_staging`). Then we run **MERGE** into the final table on `file_date` + `file_type` (and optionally row keys). Snowflake has **excellent MERGE support**. So we never expose partial data. **Idempotency:** Overwrite by file_date so duplicate consumption from Kafka still yields correct final state. **Interview:** “We write to a staging table, then MERGE into the final table by file_date and file_type. So downstream never sees partial writes; MERGE gives us idempotent upsert.”

---

### Q3.2 What tables and schema do you use? Audit in Snowflake?

**Answer:** **Tables:** e.g. `daily_positions`, `exposure_by_borrower`, `daily_pnl`. Same grain as the reports. Clustered by file_date for overwrite and pruning. **Audit / lineage:** We can store file-level audit in Snowflake (file_id, s3_uri, file_date, record_count, reject_count, processed_at) for compliance queries, or keep audit only in DynamoDB and query there.

---

### Q3.3 Security and compliance in Snowflake?

**Answer:** **Encryption:** Snowflake encrypts at rest (AES-256) and in transit (TLS). Key management (BYOK) optional. **Access:** RBAC (roles and privileges); integration with Okta, Azure AD, etc. Least privilege: Glue/Spark role can load; API role can read reporting tables; analysts have read-only. No direct Snowflake access from front end; only via API. **Interview:** “We use masking or row access policies for sensitive reports so that only authorized personas see PII or certain columns.”

---

## 4. Scenario & Design

### Q4.1 Why does the API query Snowflake directly? What about connection limits?

**Answer:** For our use case (internal reporting, T+1, a few screens, moderate concurrency) we don’t need a separate RDBMS. We use connection pooling and a small dedicated warehouse. One source of truth; fewer moving parts. If we scaled up we’d consider a sync to an RDBMS (see optional doc 09). **Connection limits:** We size the connection pool and use a dedicated warehouse for the API. Snowflake multi-cluster warehouses can scale concurrency. We monitor connection count and would add a sync only if we hit limits.

---

### Q4.2 What if Snowflake is slow or down?

**Answer:** Spark Streaming will fail the write; we don’t commit Kafka offset; we retry. For the API we’d see higher latency or errors; retry and alert. We don’t have a separate RDBMS to fall back to—that’s the tradeoff for simplicity.

---

### Q4.3 How do you avoid duplicate rows in Snowflake when consuming from Kafka at-least-once?

**Answer:** We overwrite by file_date (and file_type). Staging table + MERGE. So duplicate consumption from Kafka still yields correct final state.

---

## 5. Data Engineering Metrics (Snowflake)

### Q5.1 What Snowflake metrics do you track?

**Answer:**

| Metric | What to track | Why |
|--------|----------------|-----|
| **Load time** | Per COPY INTO or per batch | SLA; optimize. |
| **Rows loaded** | Per table per file_date | Completeness; match to source. |
| **Query queue / concurrency** | Warehouse utilization | Backlog; scale or tune. |
| **Connection count** | Active connections per warehouse | Avoid exhausting limit (API pool size). |
| **Storage / compute** | Warehouse size; credits used | Cost; right-size. |

---

## 6. Evaluation Cheat Sheet

| Area | What good looks like | Red flag |
|------|----------------------|----------|
| **API → Snowflake** | Connection pool; dedicated warehouse; simple SELECTs | “We sync to RDBMS” without explaining when |
| **Staging + MERGE** | Staging table; MERGE by file_date; idempotent | Direct insert; partial visible |
| **Clustering** | file_date, borrower_id for common filters | No clustering or vague |
| **Security** | RBAC; masking/RLS for PII; no front-end direct access | No mention of access control |

Use this when they ask “why Snowflake?” or “how does the API get its data?”—**API queries Snowflake directly; no Glue sync for our flow.**
