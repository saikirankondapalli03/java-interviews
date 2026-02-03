"""
Lambda orchestrator: SQS → parse S3 event → check DynamoDB idempotency → trigger Glue job.

Flow: S3 (ObjectCreated:Complete) → SNS → SQS → this Lambda → Glue StartJobRun.

Environment variables:
  GLUE_JOB_NAME     - Name of the Glue job to trigger (e.g. equilend-file-processor).
  AUDIT_TABLE_NAME  - DynamoDB table for idempotency/audit (optional; skip check if unset).
  SOURCE_SYSTEM     - For idempotency key (default: EQUILEND).
"""

import json
import logging
import os
import re
from typing import Any, Optional

import boto3
from botocore.exceptions import ClientError

logger = logging.getLogger(__name__)
logger.setLevel(logging.INFO)

GLUE_JOB_NAME = os.environ.get("GLUE_JOB_NAME", "equilend-file-processor")
AUDIT_TABLE_NAME = os.environ.get("AUDIT_TABLE_NAME", "")
SOURCE_SYSTEM = os.environ.get("SOURCE_SYSTEM", "EQUILEND")


def lambda_handler(event: dict, context: Any) -> dict:
    """
    Invoked by SQS (event source mapping). event["Records"] is the batch of SQS messages.
    Each record body may be:
      - SNS notification: {"Type":"Notification", "Message": "<json string of S3 event>"}
      - Or direct S3 event: {"Records":[{"s3":{"bucket":{"name":...}, "object":{"key":...}}}]}
    Returns batchItemFailures so SQS retries only failed messages (partial batch response).
    """
    batch_item_failures = []
    for record in event.get("Records", []):
        try:
            process_one_message(record)
        except Exception as e:
            # Return message ID so SQS will retry only this message (or send to DLQ after max receives)
            batch_item_failures.append({"itemIdentifier": record.get("messageId", "")})
            # Log and continue so other messages in batch can succeed
            # raise e  # uncomment to fail entire batch

    return {"batchItemFailures": batch_item_failures}


def process_one_message(record: dict) -> None:
    body = record.get("body", "{}")
    if isinstance(body, str):
        body = json.loads(body) if body.strip() else {}

    bucket, key = parse_s3_info_from_body(body)
    if not bucket or not key:
        raise ValueError(f"Could not extract bucket/key from message body: {body}")

    file_type = derive_file_type(key)
    file_date = derive_file_date(key)
    if not file_type or not file_date:
        raise ValueError(f"Could not derive file_type/file_date from key: {key}")

    file_name = key.split("/")[-1] if "/" in key else key
    idempotency_key = f"{SOURCE_SYSTEM}|{file_type}|{file_date}|{file_name}"
    s3_uri = f"s3://{bucket}/{key}"

    if AUDIT_TABLE_NAME:
        if should_skip_already_processed(idempotency_key, file_type, file_date):
            logger.info("Skipping already SUCCESS", extra={"idempotency_key": idempotency_key})
            return
        reserved = reserve_run(idempotency_key, file_type, file_date, s3_uri)
        if not reserved and not re_reserve_if_failed(idempotency_key, file_type, file_date, s3_uri):
            logger.info("Skipping: reserve failed (already PENDING or exists)", extra={"idempotency_key": idempotency_key})
            return

    try:
        job_run_id = start_glue_job(s3_uri, file_type, file_date)
        if AUDIT_TABLE_NAME and job_run_id:
            store_job_run_id(idempotency_key, file_type, file_date, job_run_id)
        logger.info("Glue job started", extra={"idempotency_key": idempotency_key, "job_run_id": job_run_id})
    except ClientError as e:
        if AUDIT_TABLE_NAME:
            mark_audit_failed(idempotency_key, file_type, file_date, str(e))
        logger.exception("Glue StartJobRun failed")
        raise


def parse_s3_info_from_body(body: dict) -> tuple[Optional[str], Optional[str]]:
    """
    S3 → SNS → SQS: body is often {"Type":"Notification", "Message": "<json string>"}.
    The Message string is the S3 event: {"Records":[{"s3":{"bucket":{"name":"..."}, "object":{"key":"..."}}}]}.
    """
    if body.get("Type") == "Notification" and "Message" in body:
        try:
            inner = json.loads(body["Message"])
        except (json.JSONDecodeError, TypeError):
            return None, None
        body = inner

    records = body.get("Records") or []
    if not records:
        return None, None
    s3_info = records[0].get("s3") or {}
    bucket = (s3_info.get("bucket") or {}).get("name")
    key = (s3_info.get("object") or {}).get("key")
    if key and isinstance(key, str):
        key = key.replace("%2F", "/")  # URL-encoded key
    return bucket, key


def derive_file_type(key: str) -> Optional[str]:
    """positions/ or mtm_pnl/ → positions | mtm_pnl."""
    if key.startswith("positions/"):
        return "positions"
    if key.startswith("mtm_pnl/"):
        return "mtm_pnl"
    return None


def derive_file_date(key: str) -> Optional[str]:
    """equilend_positions_20250201.csv → 20250201."""
    file_name = key.split("/")[-1] if "/" in key else key
    match = re.search(r"_(\d{8})\.csv$", file_name, re.IGNORECASE)
    return match.group(1) if match else None


def should_skip_already_processed(idempotency_key: str, file_type: str, file_date: str) -> bool:
    """GetItem with strong consistency: if status == SUCCESS, skip (idempotent)."""
    if not AUDIT_TABLE_NAME:
        return False
    table = boto3.resource("dynamodb").Table(AUDIT_TABLE_NAME)
    pk, sk = f"{file_type}#{file_date}", idempotency_key
    try:
        resp = table.get_item(Key={"pk": pk, "sk": sk}, ConsistentRead=True)
        item = resp.get("Item")
        return item is not None and item.get("status") == "SUCCESS"
    except ClientError:
        return False


def reserve_run(idempotency_key: str, file_type: str, file_date: str, s3_uri: str) -> bool:
    """
    Conditional put: insert audit row only if this (pk, sk) doesn't exist (status PENDING).
    Returns True if this invocation won the reservation; False if already exists (skip job).
    """
    table = boto3.resource("dynamodb").Table(AUDIT_TABLE_NAME)
    try:
        table.put_item(
            Item={
                "pk": f"{file_type}#{file_date}",
                "sk": idempotency_key,
                "idempotency_key": idempotency_key,
                "file_type": file_type,
                "file_date": file_date,
                "s3_uri": s3_uri,
                "status": "PENDING",
                "record_count": 0,
                "reject_count": 0,
            },
            ConditionExpression="attribute_not_exists(sk)",
        )
        return True
    except ClientError as e:
        if e.response["Error"]["Code"] == "ConditionalCheckFailedException":
            # Item already exists (another worker or replay); skip
            return False
        raise


def re_reserve_if_failed(idempotency_key: str, file_type: str, file_date: str, s3_uri: str) -> bool:
    """If item exists with status=FAILED (e.g. Glue start failed), set PENDING so we can retry."""
    if not AUDIT_TABLE_NAME:
        return False
    table = boto3.resource("dynamodb").Table(AUDIT_TABLE_NAME)
    pk, sk = f"{file_type}#{file_date}", idempotency_key
    try:
        table.update_item(
            Key={"pk": pk, "sk": sk},
            UpdateExpression="SET #st = :pending REMOVE error_message",
            ConditionExpression="#st = :failed",
            ExpressionAttributeNames={"#st": "status"},
            ExpressionAttributeValues={":pending": "PENDING", ":failed": "FAILED"},
        )
        return True
    except ClientError as e:
        if e.response["Error"]["Code"] == "ConditionalCheckFailedException":
            return False
        raise


def start_glue_job(input_path: str, file_type: str, file_date: str) -> Optional[str]:
    """Start Glue job with --input-path, --file-type, --file-date (used by Glue getResolvedOptions). Returns JobRunId."""
    glue = boto3.client("glue")
    resp = glue.start_job_run(
        JobName=GLUE_JOB_NAME,
        Arguments={
            "--input-path": input_path,
            "--file-type": file_type,
            "--file-date": file_date,
        },
    )
    return resp.get("JobRunId")


def store_job_run_id(idempotency_key: str, file_type: str, file_date: str, job_run_id: str) -> None:
    """Update audit row with job_id for traceability (Glue will later set status/record_count)."""
    table = boto3.resource("dynamodb").Table(AUDIT_TABLE_NAME)
    pk, sk = f"{file_type}#{file_date}", idempotency_key
    table.update_item(
        Key={"pk": pk, "sk": sk},
        UpdateExpression="SET job_id = :jid",
        ConditionExpression="attribute_exists(sk) AND #st = :pending",
        ExpressionAttributeNames={"#st": "status"},
        ExpressionAttributeValues={":jid": job_run_id, ":pending": "PENDING"},
    )


def mark_audit_failed(idempotency_key: str, file_type: str, file_date: str, error_message: str) -> None:
    """On Glue StartJobRun failure, set status=FAILED so SQS retry can reserve again and retry."""
    table = boto3.resource("dynamodb").Table(AUDIT_TABLE_NAME)
    pk, sk = f"{file_type}#{file_date}", idempotency_key
    try:
        table.update_item(
            Key={"pk": pk, "sk": sk},
            UpdateExpression="SET #st = :failed, error_message = :err",
            ConditionExpression="#st = :pending",
            ExpressionAttributeNames={"#st": "status"},
            ExpressionAttributeValues={":failed": "FAILED", ":pending": "PENDING", ":err": error_message[:500]},
        )
    except ClientError as e:
        if e.response["Error"]["Code"] != "ConditionalCheckFailedException":
            logger.warning("Could not mark audit FAILED: %s", e)
