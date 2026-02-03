# 05 – Batch Processing (Glue / PySpark)

In production: Glue job reads CSV from S3 landing, validates schema and business rules, writes valid rows to curated S3 (Parquet) and to MSK (Kafka); invalid rows to quarantine; updates DynamoDB audit.

## Flow

1. **Input**: `--input-path`, `--file-type`, `--file-date` (from orchestrator).
2. **Read** CSV from S3 (or local path for demo).
3. **Validate** per row: schema + business rules (no duplicate loan_id; quantity > 0; settle_date >= trade_date; non-empty keys).
4. **Valid** → write to curated S3 (partitioned by file_date) + publish to Kafka (MSK).
5. **Invalid** → write to quarantine S3 with reject reason.
6. **Update** DynamoDB audit: SUCCESS/PARTIAL/FAILED, record_count, reject_count.

## Code

- **PositionRecord**, **MtmPnlRecord** – curated row models.
- **PositionValidator**, **MtmPnlValidator** – validation; **ValidatedRow** – valid or reject reason.
- **FileProcessor** – read CSV, validate, write curated + quarantine, publish to **EventBus**.
- **ProcessingResult** – record_count, reject_count.

Next step: **06-event-bus** (Kafka/MSK); **07-streaming-consumer** consumes from bus and sinks to reporting store.
