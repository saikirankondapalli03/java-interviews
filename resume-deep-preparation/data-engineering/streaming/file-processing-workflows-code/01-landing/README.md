# 01 – Landing (S3 Landing Zone)

In production: Equilend drops files to an S3 bucket with prefixes `positions/` and `mtm_pnl/`. This folder holds **path configuration** for the pipeline (no infra).

## Concepts

- **Event**: `s3:ObjectCreated:Complete` (not `Put`) so multipart uploads don’t trigger on partial writes.
- **Prefixes**: `positions/`, `mtm_pnl/` – only these trigger the pipeline.
- **Consistency**: S3 is strongly consistent (read-after-write safe once Complete is returned).

## Config

- **landing-paths.properties** – Base path and prefixes used by the app. For local runs this points at `test-data/`; in AWS it would be the S3 bucket + prefix.

Next step: **02-eventing** (S3 → SNS → SQS message payload).
