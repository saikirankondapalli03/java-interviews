# Star Schema, Snowflake Schema, and Wide Tables in Analytics

## Business Context: Why Do We Model Data Differently?

In Agency Lending (Equilend) reporting, **different personas** need different views of the same underlying data:

| Persona | Question they ask | Data they need |
|---------|-------------------|----------------|
| **Operations** | "Show me every loan position for today" | Row-level: security, borrower, quantity, value |
| **Risk** | "What's our concentration by borrower?" | Aggregated by borrower |
| **Finance** | "What did we earn today by book?" | Aggregated by book/desk |
| **Compliance** | "What counterparty types drive most exposure?" | Aggregated by borrower type, region |
| **Management** | "Trend across months by business unit" | Time-series by business hierarchy |

How we *organize* the data (star vs snowflake vs wide) affects how easily we can answer these questions, how much storage we use, and how fast queries run.

---

## Business Explanation of Each Model

### 1. Wide Tables — "Put Everything in One Place"

**Business idea:** Store each report-ready row with *all* the descriptive attributes already attached. No lookups or joins—everything you need is on that row.

**Why businesses like it:**
- **Simple to understand:** One table, one report. Operations gets positions; Risk gets exposure; each screen reads one table.
- **Fast for fixed reports:** The API or dashboard knows exactly which table to hit. No complex joins.
- **Quick to build:** Spark aggregates and writes; the table is done. Good for MVP and T+1 reporting.

**Downside:**
- **Redundancy:** "Hedge Fund ABC" appears thousands of times if they have thousands of loans. Storage and updates can be costly.
- **Hard to extend:** Adding "borrower region" or "security sector" means backfilling every historical row and changing ETL.

**When to use:** Pre-defined reports (Daily Positions, Exposure by Borrower, Daily P&L) with stable columns and moderate scale. **Fits our Equilend pipeline today.**

---

### 2. Star Schema — "Separate Facts from Descriptions"

**Business idea:** Split data into **facts** (what happened: loan, value, quantity) and **dimensions** (who, what, when: borrower, security, book, date). Facts reference dimensions by ID; dimensions hold the descriptions.

**Why businesses like it:**
- **Reusable dimensions:** One `dim_borrower` row for "Hedge Fund ABC" is shared across all their loans. Change the name once, all reports update.
- **Flexible analytics:** New questions (e.g., "exposure by security sector") often need only a new dimension or a new join—facts stay the same.
- **Efficient storage:** Numbers and IDs in the fact table; text and hierarchies in dimensions. Columnar engines (Snowflake) compress this well.

**Downside:**
- **More tables to maintain:** Need ETL for dimensions (borrower, security, book, date) and for facts.
- **Joins required:** Every report joins fact + dimensions. Usually fast with proper keys and clustering.

**When to use:** When multiple reports and ad-hoc analytics share the same entities (borrowers, securities, books) and you expect to add new dimensions or drill-downs over time.

---

### 3. Snowflake Schema — "Normalize Dimensions Too"

**Business idea:** Like star schema, but dimensions are *further broken down*. Borrower → counterparty type; Book → Desk → Business Unit; Security → security type.

**Why businesses like it:**
- **Governance and consistency:** "Counterparty type" or "Business unit" are defined once. Reports that use them are consistent.
- **Compliance and hierarchies:** Regulatory reports often need business-unit or legal-entity hierarchies. Snowflake schema supports that cleanly.
- **Less redundancy:** Only unique values in each normalized table.

**Downside:**
- **More joins:** Queries may go fact → dim_borrower → dim_counterparty_type, fact → dim_book → dim_desk → dim_business_unit. More complexity, potentially slower if not tuned.
- **Harder to explain:** Business users may not care about "counterparty type" as a separate table; they just want "Hedge Fund" on the row.

**When to use:** When you have clear hierarchies (org structure, counterparty classification, security taxonomies) and need governed, reusable reference data across many reports.

---

## How This Maps to the Equilend Pipeline

### Current Design (Wide-Style)

Our pipeline loads **report-grain tables** into Snowflake:

| Table | Grain | Style | Business Use |
|-------|-------|-------|--------------|
| `daily_positions` | One row per loan | Wide | "Show me all positions" — Ops, Risk |
| `exposure_by_borrower` | One row per borrower per date | Wide | "Concentration risk" — Risk, Compliance |
| `daily_pnl` | One row per book/desk/loan per date | Wide | "P&L by book" — Finance, Management |

Each table is self-contained. The Spring Boot API runs simple `SELECT` by `file_date`, `borrower_id`, etc. No joins. **Good for fixed reports and T+1 delivery.**

---

### If We Moved to Star Schema (Future Flexibility)

```
Facts (what happened):
  fact_daily_positions  — loan_id, file_date, security_id, borrower_id, book_id, quantity, loan_value_usd, ...
  fact_daily_pnl        — file_date, book_id, loan_id, pnl_usd, rebate_income_usd, ...

Dimensions (who, what, when):
  dim_borrower   — borrower_id, borrower_name
  dim_security   — security_id, security_name
  dim_book       — book_id, desk_id
  dim_date       — file_date, trade_date, fiscal_period, ...
```

**Business benefit:** New reports (e.g., "exposure by security sector") can add `dim_security_type` and join—no need to rewrite fact tables. One source of truth for borrower, security, book.

---

### If We Moved to Snowflake Schema (Governance & Hierarchies)

```
fact_daily_positions
    ├── dim_borrower ──► dim_counterparty_type (HF, broker-dealer, bank)
    ├── dim_security ──► dim_security_type (equity, bond, ETF)
    └── dim_book ──────► dim_desk ──────► dim_business_unit
```

**Business benefit:** Regulatory and management reports that need "exposure by counterparty type" or "P&L by business unit" use the same governed hierarchies. Compliance and Finance stay aligned.

---

## Visual Summary

### Structure Comparison

```
WIDE TABLE                    STAR SCHEMA                    SNOWFLAKE SCHEMA
───────────                   ───────────                    ─────────────────

┌─────────────────────┐       dim_borrower                   dim_borrower ──► dim_counterparty_type
│ wide_daily_positions│              │                        dim_book ─────► dim_desk ──► dim_business_unit
│ file_date           │       dim_security                   dim_security ──► dim_security_type
│ loan_id             │              │                                │
│ security_id         │       dim_book│                                │
│ security_name ◄─────┼───denormalized        fact_positions ◄────────┘
│ borrower_id         │              │
│ borrower_name ◄─────┼───denormalized       (IDs only; join for names)
│ book_id             │
│ quantity            │       One fact table, many dimension lookups
│ loan_value_usd      │
└─────────────────────┘

Everything on one row.         Facts + dimensions.            Dimensions normalized further.
Simple, fixed reports.         Flexible, reusable.            Governed hierarchies.
```

### Example: Same 3 Loans in Each Model

Real agency lending positions from Equilend (file_date 2025-01-28):

**Wide Table** — All attributes on every row (redundant names):

| file_date   | loan_id              | security_id  | security_name   | borrower_id | borrower_name              | book_id     | loan_value_usd |
|-------------|----------------------|--------------|-----------------|-------------|----------------------------|-------------|----------------|
| 2025-01-28  | EQ-POS-20250128-48921 | 037833100    | Apple Inc.      | 62883       | Citadel Securities LLC     | AGY-US-EQ   | 5250000.00     |
| 2025-01-28  | EQ-POS-20250128-48922 | 037833100    | Apple Inc.      | 62883       | Citadel Securities LLC     | AGY-US-EQ   | 3180000.00     |
| 2025-01-28  | EQ-POS-20250128-48923 | 594918104    | Microsoft Corp. | 07560       | Morgan Stanley & Co. LLC   | AGY-US-EQ   | 12850000.00    |

*"Citadel Securities LLC" and "Apple Inc." repeated—storage cost; no joins. CUSIP 037833100 = AAPL, 594918104 = MSFT; borrower 62883 = Citadel MPID.*

---

**Star Schema** — Facts store IDs; dimensions hold descriptions:

`fact_positions`:

| file_date  | loan_id              | security_id | borrower_id | book_id   | loan_value_usd |
|------------|----------------------|-------------|-------------|-----------|----------------|
| 2025-01-28 | EQ-POS-20250128-48921 | 037833100   | 62883       | AGY-US-EQ | 5250000.00     |
| 2025-01-28 | EQ-POS-20250128-48922 | 037833100   | 62883       | AGY-US-EQ | 3180000.00     |
| 2025-01-28 | EQ-POS-20250128-48923 | 594918104   | 07560       | AGY-US-EQ | 12850000.00    |

`dim_borrower`:

| borrower_id | borrower_name              |
|-------------|----------------------------|
| 62883       | Citadel Securities LLC     |
| 07560       | Morgan Stanley & Co. LLC   |

`dim_security`:

| security_id | security_name   |
|-------------|-----------------|
| 037833100   | Apple Inc.      |
| 594918104   | Microsoft Corp. |

*Names stored once; join to get "Exposure by Citadel Securities LLC" (8.43M) vs "Morgan Stanley" (12.85M).*

---

**Snowflake Schema** — Dimensions further normalized:

`dim_borrower` → `dim_counterparty_type`:

| borrower_id | borrower_name              | counterparty_type_id |
|-------------|----------------------------|----------------------|
| 62883       | Citadel Securities LLC     | MKT                  |
| 07560       | Morgan Stanley & Co. LLC   | BDL                  |

`dim_counterparty_type`:

| counterparty_type_id | counterparty_type |
|----------------------|-------------------|
| MKT                  | Market Maker      |
| BDL                  | Broker-Dealer     |

*Query "Exposure by counterparty type" (Market Maker: 8.43M; Broker-Dealer: 12.85M) without duplicating type on every borrower row.*

---

### Example: Same Question, Different Queries

**"What is total exposure by borrower for 2025-01-28?"**

**Wide table** — No joins:

```sql
SELECT borrower_name, SUM(loan_value_usd) AS total_exposure
FROM wide_daily_positions
WHERE file_date = '2025-01-28'
GROUP BY borrower_name;
```

*Result: Citadel Securities LLC | 8,430,000.00; Morgan Stanley & Co. LLC | 12,850,000.00*

---

**Star schema** — Join fact to dimension:

```sql
SELECT b.borrower_name, SUM(f.loan_value_usd) AS total_exposure
FROM fact_positions f
JOIN dim_borrower b ON f.borrower_id = b.borrower_id
WHERE f.file_date = '2025-01-28'
GROUP BY b.borrower_name;
```

*Result: same as above.*

---

**Snowflake schema** — Join through counterparty type ("exposure by counterparty type"):

```sql
SELECT ct.counterparty_type, SUM(f.loan_value_usd) AS total_exposure
FROM fact_positions f
JOIN dim_borrower b ON f.borrower_id = b.borrower_id
JOIN dim_counterparty_type ct ON b.counterparty_type_id = ct.counterparty_type_id
WHERE f.file_date = '2025-01-28'
GROUP BY ct.counterparty_type;
```

*Result: Market Maker | 8,430,000.00; Broker-Dealer | 12,850,000.00*

---

## Interview One-Liner

> "We use wide-style tables at report grain for our Equilend pipeline—simple, fast for the API. If we needed richer analytics across borrower types, business units, or security sectors, we'd introduce a star schema with fact and dimension tables. Snowflake schema would add value when we have strict governance and hierarchy requirements for compliance."

---

## References

- Pipeline: `INTERVIEW-PREP-EQUILEND-FILE-WORKFLOW.md`
- Snowflake component: `components/08-SNOWFLAKE.md`
- Spark Streaming → Snowflake: `components/07-SPARK-STREAMING-SNOWFLAKE.md`
