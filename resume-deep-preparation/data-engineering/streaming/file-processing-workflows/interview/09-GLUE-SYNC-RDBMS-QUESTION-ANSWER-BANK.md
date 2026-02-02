# Component 09: Optional Snowflake → RDBMS Sync — Question & Answer Bank

**Reference:** `components/09-GLUE-SYNC-RDBMS.md`  
**Interview coverage:** Intro, when to add, design, evaluation. **Not in our main flow.**

---

## 1. Intro & Context (Opening)

### Q1.1 Is there a Glue sync from Snowflake to an RDBMS in this pipeline?

**Answer:** **No.** For this use case (internal reporting, T+1, a few screens, moderate concurrency) the **Spring Boot API queries Snowflake directly** with connection pooling. No Glue sync. Keep this doc for interviews: “We could add a sync to an RDBMS if we scaled up API traffic or hit connection limits; for now we keep it simple.”

---

### Q1.2 When would you add a Snowflake → RDBMS sync?

**Answer:** Add a Glue (or Spark) job that syncs reporting aggregates from Snowflake into PostgreSQL/Aurora **only if**:

- **High API concurrency** — hundreds or thousands of concurrent users; Snowflake connection limits or cost become a problem.
- **Strict latency** — you need sub-200ms p95 and don’t want API and analytics sharing the same warehouse.
- **Cost** — hitting Snowflake for every UI request is too expensive; one batch sync per day is cheaper.

Then: Snowflake remains source of truth; Glue sync runs on a schedule (e.g. after T+1 load); API queries the RDBMS. Same pattern: read from Snowflake via JDBC, overwrite RDBMS tables by file_date, idempotent.

---

## 2. Architecture & Design (If You Add It)

### Q2.1 How would you implement the sync job?

**Answer:** Glue (or Spark) job: read from Snowflake via JDBC (or unload to S3 then load to RDBMS). Overwrite RDBMS tables by file_date (and file_type). Idempotent: same run twice → same state. Schedule: e.g. after T+1 Glue/Spark Streaming load completes (cron or Step Functions). API then points to RDBMS instead of Snowflake for report queries.

---

### Q2.2 What would you sync—full tables or aggregates?

**Answer:** Typically the same report grain: daily_positions, exposure_by_borrower, daily_pnl. Full table overwrite by file_date or incremental if we had a different design. Keep it simple: overwrite by file_date so idempotency is straightforward.

---

### Q2.3 How do you monitor and alert for the sync job?

**Answer:** CloudWatch alarm on Glue sync job failure. Metric: “last successful sync time” or “rows synced per run.” Alert if sync fails so RDBMS doesn’t go stale.

---

## 3. Scenario & Follow-ups

### Q3.1 “Our API is slow / we’re hitting Snowflake connection limits. What do we do?”

**Answer:** First: size connection pool, use a dedicated warehouse, consider multi-cluster warehouse. If we still hit limits or cost: add a Glue sync from Snowflake to PostgreSQL/Aurora; API queries RDBMS. Snowflake stays source of truth; sync runs after T+1 load.

---

### Q3.2 “We need sub-100ms p95 for the API. Snowflake is too variable.”

**Answer:** Add sync to RDBMS; API queries RDBMS for report endpoints. Snowflake for ad-hoc and compliance; RDBMS for low-latency API.

---

## 4. Evaluation Cheat Sheet

| Area | What good looks like | Red flag |
|------|----------------------|----------|
| **When to add** | High concurrency, strict latency, or cost; clear criteria | “We always sync” or “We never sync” without reasoning |
| **Our design** | API → Snowflake directly; no sync for current scale | Unclear whether we sync or not |
| **If added** | Idempotent overwrite by file_date; Snowflake = source of truth | Sync as primary source; no idempotency |

Use this when they ask “do you sync to a relational DB?” or “when would you add a sync?”
