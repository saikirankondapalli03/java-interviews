# 01 – Lambda (SQS → Glue)

Sample Python Lambda that consumes SQS messages (from S3 → SNS → SQS) and triggers a Glue job.

## Flow

1. **S3** file lands → **S3 event** (ObjectCreated:Complete) → **SNS** → **SQS**.
2. **Lambda** is invoked by SQS (event source mapping).
3. Lambda parses each message: extract **bucket**, **key** (handles SNS-wrapped S3 event body).
4. Derives **file_type** (positions | mtm_pnl) from key prefix and **file_date** (YYYYMMDD) from filename.
5. Optionally **DynamoDB**: conditional put to reserve run (idempotency); skip if already exists.
6. **Glue** `StartJobRun` with arguments: `--input-path`, `--file-type`, `--file-date`.

## Files

- **lambda_handler.py** – Handler; `lambda_handler(event, context)` for SQS event.

## Environment variables

| Variable | Description |
|----------|-------------|
| `GLUE_JOB_NAME` | Glue job name to trigger (e.g. `equilend-file-processor`). |
| `AUDIT_TABLE_NAME` | DynamoDB table for idempotency (PK: `file_type#file_date`, SK: idempotency key). Omit to skip idempotency check. |
| `SOURCE_SYSTEM` | Source system in idempotency key (default: `EQUILEND`). |

## IAM (Lambda execution role)

- **SQS**: `sqs:ReceiveMessage`, `sqs:DeleteMessage`, `sqs:GetQueueAttributes` on the queue.
- **Glue**: `glue:StartJobRun` on the job (or `glue:*` for the job resource).
- **DynamoDB** (if using audit): `dynamodb:PutItem`, `dynamodb:GetItem`, `dynamodb:ConditionCheckItem` on the audit table.

## SQS message body (S3 → SNS → SQS)

SNS wraps the S3 event; body looks like:

```json
{
  "Type": "Notification",
  "Message": "{\"Records\":[{\"s3\":{\"bucket\":{\"name\":\"my-bucket\"},\"object\":{\"key\":\"positions/equilend_positions_20250201.csv\"}}}]}"
}
```

The handler parses `Message` as JSON to get bucket and key.

## Test event (SQS)

Use this as a test event for the Lambda (single record):

```json
{
  "Records": [
    {
      "messageId": "test-msg-1",
      "body": "{\"Type\":\"Notification\",\"Message\":\"{\\\"Records\\\":[{\\\"s3\\\":{\\\"bucket\\\":{\\\"name\\\":\"my-landing-bucket\\\"},\\\"object\\\":{\\\"key\\\":\"positions/equilend_positions_20250201.csv\\\"}}}]}\"}"
    }
  ]
}
```

Or direct S3 event (no SNS wrapper):

```json
{
  "Records": [
    {
      "messageId": "test-msg-1",
      "body": "{\"Records\":[{\"s3\":{\"bucket\":{\"name\":\"my-landing-bucket\"},\"object\":{\"key\":\"positions/equilend_positions_20250201.csv\"}}}]}"
    }
  ]
}
```

## Deployment

- Package `lambda_handler.py` (and any dependencies; boto3 is in Lambda runtime).
- Create Lambda with Python 3.11+ runtime, set handler to `lambda_handler.lambda_handler`.
- Add SQS trigger (event source mapping) to the queue.
- Set visibility timeout on SQS **≥ max Glue job duration** (e.g. 1–2 hours) so the message isn’t reprocessed while Glue runs.
- Glue job completion: have the Glue script update DynamoDB (SUCCESS/FAILED) at end, or use EventBridge (Glue job state change) → Lambda to update audit and/or Step Functions callback.

---

## Interview talking points (what this sample covers)

| Topic | What we do |
|-------|------------|
| **Idempotency** | GetItem (strong consistency) first: if status == SUCCESS, skip. Conditional put to reserve (attribute_not_exists(sk)); only one worker wins. |
| **Skip vs retry** | SUCCESS → skip. PENDING → skip (job already started). FAILED → re-reserve (update FAILED→PENDING) so SQS retry can start Glue again. |
| **Glue failure** | If StartJobRun fails, we mark audit FAILED so message stays in SQS; on retry we re-reserve and retry Glue. |
| **Traceability** | After starting job we store JobRunId in audit (job_id); Glue later sets status, record_count, reject_count, processed_at. |
| **Partial batch** | Return batchItemFailures so SQS retries only failed messages; others are deleted. |
| **Logging** | INFO for skip/reserve/start; WARNING/exception on Glue failure. |

**Things to mention in interview (not in code):**

- **Visibility timeout**: Set SQS visibility timeout ≥ max Glue job duration (e.g. 1–2 hours) so the message isn’t reprocessed while Glue runs. We delete on success via Lambda’s normal return (SQS event source deletes); if we don’t return batchItemFailures for a message, it’s deleted.
- **Reserved concurrency**: Limit Lambda concurrency (e.g. 2) so we don’t start too many Glue jobs at once; SQS depth acts as backpressure.
- **DLQ**: Configure max receives (e.g. 3) on the main queue; failed messages go to DLQ. Alarm on DLQ depth > 0.
- **Who updates audit on completion**: Glue script updates DynamoDB (SUCCESS/PARTIAL/FAILED, record_count, reject_count, processed_at) at end of job; or EventBridge (Glue job state change) → Lambda → UpdateItem.
- **Prefix filter**: We only process keys under positions/ or mtm_pnl/; other keys raise and go to retry/DLQ (or you could skip and delete).

---

## Next

Orchestration (this Lambda) → **Glue** job (batch processing). See **`../02-glue/`** for the Glue PySpark script and README. Concepts: `../glue-interview-prep` and component doc `05-GLUE-PYSPARK-BATCH.md`.
