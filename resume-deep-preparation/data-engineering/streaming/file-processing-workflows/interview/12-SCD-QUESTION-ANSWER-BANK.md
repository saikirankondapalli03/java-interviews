# Topic: Slowly Changing Dimensions (SCD) — Question & Answer Bank

**Reference:** Cross-cutting; applies when building **dimensions** or when **history** is required. In this flow: `components/08-SNOWFLAKE.md` (staging + MERGE, overwrite by file_date), `components/05-GLUE-PYSPARK-BATCH.md`, and `examples-to-understand-concepts/STAR-SNOWFLAKE-WIDE-TABLES-ANALYTICS.md` (fact/dimension design).  
**Interview coverage:** SCD types, when to use which, implementation in Snowflake/Glue, and how it fits the Equilend-style file workflow.

---

## 1. Intro & Context (Opening)

### Q1.1 What are slowly changing dimensions (SCD), and why do they matter in a reporting pipeline?

**Answer:** **SCD** = how we handle **changes over time** to dimension attributes (e.g. borrower name, book desk, security sector). If we only overwrite, we lose history; if we keep every change, we can report “as of” a point in time and support compliance. In a **file-based T+1 pipeline** we often have **snapshot files** (e.g. positions by file_date). The choice of SCD type (1, 2, 3) determines whether we keep history and how we model it. **Interview:** “We use Type 1 (overwrite by file_date) for daily snapshot tables. When we need history—e.g. borrower attributes or book hierarchy—we’d use Type 2 in the curated layer or in Snowflake and document the choice.”

---

### Q1.2 Where does SCD show up in this file processing workflow?

**Answer:** **Type 1:** Our main pattern—overwrite by `file_date` (and file_type). Glue writes to curated S3 / Spark sinks to Snowflake; staging + MERGE by file_date gives **idempotent overwrite**. No history; “current” state only. **Type 2 (if we add it):** For **dimension** tables (e.g. `dim_borrower`, `dim_book`) when business requires “what was the borrower name as of report date?” We’d add `effective_date`, `end_date`, `is_current` and MERGE to insert new rows and close old ones. **Interview:** “In this pipeline we implement Type 1 by design—overwrite by file_date. SCD Type 2 would apply if we introduced star schema dimensions and needed point-in-time correctness for attributes.”

---

## 2. Architecture & Ownership

### Q2.1 Explain Type 1, Type 2, and Type 3 SCD. When would you use each?

**Answer:**  
- **Type 1 (overwrite):** New value overwrites old; no history. Use when “current state only” is enough (e.g. daily snapshot by file_date, reference data that doesn’t need history). **Our default for file_date-grained report tables.**  
- **Type 2 (add row / effective dates):** Keep full history; each change gets a new row with `effective_date`, `end_date` (or `is_current`). Use when compliance or analytics need “as of” reporting (e.g. borrower name change, desk reorg).  
- **Type 3 (add column):** Keep “previous” in an extra column (e.g. `name`, `previous_name`). Limited history; use when you only need one prior value. Less common in warehouse pipelines.  

**Interview:** “For file-based T+1 we use Type 1 for daily_positions, exposure_by_borrower, daily_pnl. If we needed history for dimension attributes we’d use Type 2 with effective/end dates and a current flag; we’d document it in the model.”

---

### Q2.2 How does Type 1 map to our staging + MERGE pattern in Snowflake?

**Answer:** Spark/Glue writes to a **staging** table; we **MERGE** into the final table on `file_date` (+ file_type and row keys). MERGE semantics: match → update, no match → insert. So we **overwrite** the slice for that file_date (Type 1). Duplicate consumption from Kafka still yields correct final state. **Interview:** “Our staging + MERGE by file_date is exactly Type 1: we replace the day’s data each time. No effective_date or history columns; we don’t need them for these report tables.”

---

## 3. Technical Depth (Implementation)

### Q3.1 How would you implement Type 2 SCD in Snowflake for a dimension (e.g. dim_borrower)?

**Answer:** Dimension table has: business key (e.g. `borrower_id`), attributes (name, tier, etc.), `effective_date`, `end_date` (or `valid_to`), and `is_current` (e.g. 1 = current row). **MERGE logic:** (1) For incoming rows, compare attribute hash or key columns to current row. (2) If changed: set `end_date = current_date` and `is_current = 0` for the old row; insert new row with `effective_date = current_date`, `end_date = NULL` (or high date), `is_current = 1`. (3) Use staging table for incoming data; MERGE into dimension. **Interview:** “We’d do Type 2 in Snowflake with effective/end dates and is_current. Staging holds the delta; MERGE closes the old row and inserts the new one. Queries filter by effective_date <= report_date < end_date for point-in-time.”

---

### Q3.2 How does idempotency work with SCD in this pipeline?

**Answer:** **Type 1:** Idempotent by design—same file_date always produces same final state (overwrite). DynamoDB idempotency key (file_id / path) prevents duplicate Glue runs; duplicate Kafka consumption still yields same MERGE result. **Type 2:** Idempotency is per “version” of the dimension row. Reprocessing the same source file should not insert duplicate dimension rows; we use business key + effective_date (or attribute hash) to detect no change and skip insert. **Interview:** “For Type 1, idempotency is overwrite by file_date. For Type 2 we’d ensure the same source doesn’t create duplicate dimension versions—e.g. by comparing attribute hash before insert.”

---

## 4. Scenario & Design

### Q4.1 “We need to report exposure by borrower as of last month; borrower names have changed since then.” How do you support that?

**Answer:** Use **Type 2** for the borrower dimension. Store `borrower_id`, `borrower_name`, `effective_date`, `end_date`, `is_current`. Report query: join fact (exposure) to `dim_borrower` on `borrower_id` and `effective_date <= report_date < end_date` (or use `is_current` for “as of today”). **Interview:** “We’d introduce a Type 2 borrower dimension with effective/end dates. The exposure report would join to the dimension with a date predicate so we get the borrower name that was valid as of the report date.”

---

### Q4.2 Should we use Type 2 for daily_positions in this pipeline?

**Answer:** **Usually no.** daily_positions is a **snapshot by file_date**—we want “positions as of that file.” Overwriting by file_date (Type 1) is correct: one row set per file_date. Type 2 is for **dimension** attributes (borrower, book, security) that change over time and are referenced across many facts. **Interview:** “daily_positions stays Type 1—one snapshot per file_date. Type 2 we’d use for dimensions like borrower or book if we need point-in-time attribute reporting.”

---

## 5. Follow-ups & Seniority

### Q5.1 How do you decide between Type 1 and Type 2 for a new table?

**Answer:** **Type 1:** “Current state only” is enough; no “as of date” reporting; simpler and less storage. **Type 2:** Compliance or analytics need “what was the value at time T?”; dimension attributes that change (name, hierarchy, classification). Document the choice in the data model. **Interview:** “We use Type 1 for daily snapshot and most report tables. Type 2 when we need history for dimensions; we document it and implement effective/end dates and is_current.”

---

### Q5.2 How does SCD relate to star schema in this workflow?

**Answer:** In a **star schema**, facts (e.g. exposure, P&L) reference **dimensions** (borrower, book, security, date). Dimensions are where SCD applies: Type 1 = overwrite dimension attributes; Type 2 = keep history for dimension attributes. Facts often have a **surrogate key** to the dimension (e.g. dim_borrower_sk) so Type 2 joins are on key + date. **Interview:** “For Equilend we have pre-aggregated report grain; if we moved to star schema, dimensions would be where we’d apply Type 2 for borrower/book attributes; facts would reference dimension keys.”

---

## 6. Evaluation Cheat Sheet (SCD)

| Area | What good looks like | Red flag |
|------|----------------------|----------|
| **Type choice** | Clear reasoning: Type 1 for snapshot/current state; Type 2 when history or “as of” is required | “We always use Type 2” or no mention of tradeoffs |
| **Implementation** | Knows MERGE, effective_date/end_date/is_current for Type 2; staging + overwrite for Type 1 | Vague “we keep history” with no schema or MERGE logic |
| **Pipeline fit** | Ties to file_date overwrite, idempotency, and optional star schema dimensions | SCD discussed in isolation from the actual flow |
| **Documentation** | Documents SCD choice per table (e.g. “Type 1 for daily_positions; Type 2 for dim_borrower if required”) | No documentation of where and why each type is used |

Use this bank when they ask “slowly changing dimensions,” “Type 1 vs Type 2,” or “how do you handle history for dimensions?” — **Type 1 by default (overwrite by file_date); Type 2 when we need point-in-time for dimension attributes.**
