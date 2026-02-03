# 02 – Eventing (S3 → SNS → SQS)

In production: S3 event notification (`ObjectCreated:Complete`) → SNS topic → SQS queue. This folder documents the **message payload** the orchestrator consumes.

## Message content

- **bucket** (or logical base path for local)
- **key** – object key (e.g. `positions/equilend_positions_20250201.csv`)
- **file_size** – bytes (optional)
- **event_time** – when the event was emitted (optional)
- **etag** – optional, for extra idempotency

## Code

- **FileArrivalEvent** – POJO for the SQS message body (or S3 event detail).
- **FileType** – enum: POSITIONS, MTM_PNL (derived from key prefix).

Next step: **03-orchestration** (read SQS, check idempotency, start processor).
