# 03 – Orchestration (Lambda / Step Functions)

In production: Orchestrator reads SQS, checks DynamoDB (idempotency); if not SUCCESS, reserves run and starts Glue job with `--input-path`, `--file-type`, `--file-date`.

## Flow

1. Receive **FileArrivalEvent** (from SQS).
2. Build **idempotency key**: `source_system|file_type|file_date|file_name`.
3. **Get** audit record (strongly consistent). If exists and status = SUCCESS → skip (or optionally overwrite).
4. **Reserve run**: conditional PutItem (only if not exists). One worker wins.
5. Start **batch processor** (Glue in prod) with input-path, file-type, file-date.
6. Completion: Glue (or completion Lambda) updates DynamoDB with SUCCESS/PARTIAL/FAILED.

## Code

- **Orchestrator** – accepts FileArrivalEvent, checks audit, reserves, invokes FileProcessor.

Next step: **04-audit-idempotency** (audit store); then **05-batch-processing** (actual file processing).
