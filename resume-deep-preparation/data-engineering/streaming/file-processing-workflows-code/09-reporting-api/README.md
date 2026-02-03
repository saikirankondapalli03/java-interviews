# 09 – Reporting API (Spring Boot)

In production: Spring Boot API on EKS queries Snowflake directly (JDBC + connection pooling). Angular app consumes these endpoints for Daily Positions, Exposure by Borrower, Daily P&L.

## Endpoints

- **GET /api/reports/daily-positions?fileDate=YYYYMMDD** – Daily loan positions.
- **GET /api/reports/exposure-by-borrower?fileDate=YYYYMMDD** – Exposure by borrower (aggregate).
- **GET /api/reports/daily-pnl?fileDate=YYYYMMDD** – Daily P&L by book/desk/loan.

## Code

- **ReportController** – REST controller; delegates to **ReportingStore**.
- **Application** – Spring Boot main; configures ReportingStore (in-memory for demo).

This completes the flow: test-data → landing → eventing → orchestration → audit → batch → event-bus → streaming → reporting-store → reporting-api.
