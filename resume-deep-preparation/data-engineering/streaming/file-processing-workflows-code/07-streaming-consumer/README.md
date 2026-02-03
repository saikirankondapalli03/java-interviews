# 07 – Streaming Consumer (Spark Streaming → Snowflake)

In production: Spark Streaming (or Glue Streaming) consumes from MSK, aggregates (e.g. by borrower_id, by book_id), sinks to Snowflake (staging table → MERGE by file_date).

## Flow

1. Consume **CuratedRecordEvent** from event bus (Kafka in prod).
2. **Positions** → upsert into daily_positions; aggregate by borrower → exposure_by_borrower.
3. **MTM P&L** → upsert into daily_pnl (by file_date, book_id, desk_id, loan_id).
4. **Staging then MERGE** by file_date so downstream sees only complete partitions.

## Code

- **StreamingConsumer** – subscribes to EventBus; on event, delegates to **ReportingStore** (upsert positions, exposure, pnl).
- **ReportingStore** – interface for reporting tables; **InMemoryReportingStore** or H2 for demo.

Next step: **08-reporting-store** (Snowflake equivalent); **09-reporting-api** (Spring Boot queries store).
