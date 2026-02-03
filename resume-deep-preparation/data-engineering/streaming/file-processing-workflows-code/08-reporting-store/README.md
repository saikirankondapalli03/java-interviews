# 08 – Reporting Store (Snowflake)

In production: Snowflake is the source of truth. Spark Streaming loads staging table then MERGE into final by file_date. API queries Snowflake directly (JDBC + connection pooling).

## Tables (logical)

- **daily_positions** – one row per loan (file_date, loan_id, security_id, borrower_id, book_id, desk_id, quantity, loan_value_usd).
- **exposure_by_borrower** – aggregate by borrower (file_date, borrower_id, loan_count, total_loan_value_usd).
- **daily_pnl** – one row per loan P&L (file_date, book_id, desk_id, loan_id, pnl_usd).

## Code

- **ReportingStore** – interface: upsertPositions, upsertExposureByBorrower, upsertDailyPnl; getDailyPositions, getExposureByBorrower, getDailyPnl.
- **InMemoryReportingStore** – in-memory implementation for local/demo.

Next step: **09-reporting-api** (Spring Boot REST API that queries the store).
