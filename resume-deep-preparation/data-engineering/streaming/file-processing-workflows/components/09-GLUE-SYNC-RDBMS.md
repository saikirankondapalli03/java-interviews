# Optional: Snowflake → RDBMS Sync (When You'd Add It)

**Not in our main flow.** For this use case (internal reporting, T+1, a few screens, moderate concurrency) the **Spring Boot API queries Snowflake directly** with connection pooling. No Glue sync.

---

## When to Add a Sync (Snowflake → RDBMS)

Add a Glue (or Spark) job that syncs reporting aggregates from Snowflake into PostgreSQL/Aurora **only if**:

- **High API concurrency** — hundreds or thousands of concurrent users; Snowflake connection limits or cost become a problem.
- **Strict latency** — you need sub-200ms p95 and don't want API and analytics sharing the same warehouse.
- **Cost** — hitting Snowflake for every UI request is too expensive; one batch sync per day is cheaper.

Then: Snowflake remains source of truth; Glue sync runs on a schedule (e.g. after T+1 load); API queries the RDBMS. Same pattern as before: read from Snowflake via JDBC, overwrite RDBMS tables by file_date, idempotent.

---

## Our Design

We **don't** use this sync. API → Snowflake directly. Keep this doc for interviews: "We could add a sync to an RDBMS if we scaled up API traffic or hit connection limits; for now we keep it simple."
