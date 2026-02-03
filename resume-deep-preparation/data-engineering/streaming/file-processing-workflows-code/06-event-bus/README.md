# 06 – Event Bus (MSK / Kafka)

In production: Amazon MSK (Kafka). Topics: `equilend.positions.curated`, `equilend.mtm_pnl.curated`. Batch job publishes curated rows; Spark Streaming (or Glue Streaming) consumes.

## Concepts

- **Partitioning**: by file_date (or borrower_id) for ordering and consumer parallelism.
- **At-least-once** producer + idempotent consumer (overwrite by file_date in Snowflake).
- **Consumer lag** as key metric; alarm if lag grows.

## Code

- **CuratedRecordEvent** – type (POSITIONS / MTM_PNL), file_date, payload (PositionRecord or MtmPnlRecord).
- **EventBus** – publish, subscribe(CuratedRecordConsumer).
- **InMemoryEventBus** – in-memory implementation for local/demo.

Next step: **07-streaming-consumer** (consume from bus, aggregate, sink to reporting store).
