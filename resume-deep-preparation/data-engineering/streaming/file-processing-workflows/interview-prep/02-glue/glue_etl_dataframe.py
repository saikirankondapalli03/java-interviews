"""
AWS Glue (PySpark) batch job: Equilend file processing — DATAFRAME STYLE.

Same behavior as glue_etl.py but validation and deduplication are implemented
entirely with the DataFrame API (col, when, filter, Window, row_number, etc.).
No spark.sql for business logic. Use this for interview prep when the
interviewer prefers DataFrame-style PySpark.

Invoked by the orchestrator Lambda with:
  --input-path s3://bucket/key
  --file-type positions | mtm_pnl
  --file-date YYYYMMDD
"""

import sys
from datetime import datetime
from typing import Optional
from urllib.parse import urlparse

from pyspark.sql import SparkSession
from pyspark.sql import DataFrame
from pyspark.sql.types import StructType, StructField, StringType
from pyspark.sql.functions import col, when, lit, to_date, row_number
from pyspark.sql.window import Window
from awsglue.context import GlueContext
from awsglue.utils import getResolvedOptions
from awsglue.job import Job

import boto3
from botocore.exceptions import ClientError

# --- Job arguments ---
args = getResolvedOptions(
    sys.argv,
    [
        "JOB_NAME",
        "input_path",
        "file_type",
        "file_date",
    ],
)

def _get_optional_arg(name: str, default: str = "") -> str:
    for i, a in enumerate(sys.argv):
        if a == f"--{name}" and i + 1 < len(sys.argv):
            return sys.argv[i + 1]
    return default

audit_table_name = _get_optional_arg("audit_table_name", "")
curated_bucket = _get_optional_arg("curated_bucket", "")
quarantine_bucket = _get_optional_arg("quarantine_bucket", "")
source_system = _get_optional_arg("source_system", "EQUILEND")

input_path = args["input_path"]
file_type = args["file_type"]
file_date = args["file_date"]

if not curated_bucket and input_path.startswith("s3://"):
    parsed = urlparse(input_path)
    curated_bucket = parsed.netloc
if not quarantine_bucket:
    quarantine_bucket = curated_bucket

s3_key = urlparse(input_path).path.lstrip("/")
filename = s3_key.split("/")[-1] if "/" in s3_key else s3_key
idempotency_key = f"{source_system}|{file_type}|{file_date}|{filename}"
pk = f"{file_type}#{file_date}"
sk = idempotency_key

# --- Glue / Spark init ---
spark = SparkSession.builder.getOrCreate()
glueContext = GlueContext(spark.sparkContext)
job = Job(glueContext)
job.init(args["JOB_NAME"], args)

# --- Schemas ---
POSITIONS_STRING_SCHEMA = StructType([
    StructField("loan_id", StringType(), True),
    StructField("trade_date", StringType(), True),
    StructField("settle_date", StringType(), True),
    StructField("security_id", StringType(), True),
    StructField("security_name", StringType(), True),
    StructField("borrower_id", StringType(), True),
    StructField("borrower_name", StringType(), True),
    StructField("quantity", StringType(), True),
    StructField("loan_value_usd", StringType(), True),
    StructField("rebate_rate_bps", StringType(), True),
    StructField("fee_rate_bps", StringType(), True),
    StructField("collateral_type", StringType(), True),
    StructField("collateral_value_usd", StringType(), True),
    StructField("book_id", StringType(), True),
    StructField("desk_id", StringType(), True),
    StructField("source_system", StringType(), True),
    StructField("file_date", StringType(), True),
])

MTM_PNL_STRING_SCHEMA = StructType([
    StructField("file_date", StringType(), True),
    StructField("book_id", StringType(), True),
    StructField("desk_id", StringType(), True),
    StructField("loan_id", StringType(), True),
    StructField("mtm_value_prev_usd", StringType(), True),
    StructField("mtm_value_current_usd", StringType(), True),
    StructField("pnl_usd", StringType(), True),
    StructField("rebate_income_usd", StringType(), True),
    StructField("fee_income_usd", StringType(), True),
    StructField("source_system", StringType(), True),
])


def read_csv_with_bookmark(
    glue_context: GlueContext, path: str, schema: StructType, transformation_ctx: str = "equilend_source"
) -> DataFrame:
    """Read CSV from S3 (Glue DynamicFrame with bookmark); convert to DataFrame with string schema."""
    dyf = glue_context.create_dynamic_frame.from_options(
        connection_type="s3",
        connection_options={"paths": [path]},
        format="csv",
        format_options={"withHeader": True, "separator": ","},
        transformation_ctx=transformation_ctx,
    )
    df = dyf.toDF()
    for f in schema.fields:
        if f.name in df.columns:
            df = df.withColumn(f.name, col(f.name).cast("string"))
        else:
            df = df.withColumn(f.name, lit(None).cast("string"))
    return df.select([f.name for f in schema.fields])


def validate_positions(df: DataFrame) -> tuple[DataFrame, DataFrame]:
    """
    Validate positions using DataFrame API only.
    Required non-empty; quantity > 0; loan_value_usd >= 0; settle_date >= trade_date; dedupe by loan_id.
    """
    required_str = ["loan_id", "security_id", "borrower_id", "book_id", "desk_id", "trade_date", "settle_date", "file_date"]
    cond = lit(True)
    for c in required_str:
        cond = cond & (col(c).isNotNull() & (col(c) != ""))

    qty_ok = col("quantity").cast("double").isNotNull() & (col("quantity").cast("double") > 0)
    loan_val_ok = col("loan_value_usd").cast("double").isNotNull() & (col("loan_value_usd").cast("double") >= 0)
    settle_ge_trade = (
        to_date(col("settle_date"), "yyyy-MM-dd").isNotNull()
        & to_date(col("trade_date"), "yyyy-MM-dd").isNotNull()
        & (to_date(col("settle_date"), "yyyy-MM-dd") >= to_date(col("trade_date"), "yyyy-MM-dd"))
    )

    reject_reason = when(~cond, lit("missing_or_empty_required")).when(
        ~qty_ok, lit("quantity_invalid_or_not_positive")
    ).when(~loan_val_ok, lit("loan_value_invalid_or_negative")).when(
        ~settle_ge_trade, lit("settle_date_before_trade_date")
    ).otherwise(lit(""))

    df_with_reason = df.withColumn("_reject_reason", reject_reason)
    valid_df = df_with_reason.filter(col("_reject_reason") == "").drop("_reject_reason")
    invalid_df = df_with_reason.filter(col("_reject_reason") != "").withColumnRenamed("_reject_reason", "reject_reason")

    w = Window.partitionBy("loan_id").orderBy(col("loan_id"))
    valid_dedup = valid_df.withColumn("_rn", row_number().over(w)).filter(col("_rn") == 1).drop("_rn")
    duplicate_loan_ids = valid_df.withColumn("_rn", row_number().over(w)).filter(col("_rn") > 1).drop("_rn")
    if duplicate_loan_ids.count() > 0:
        invalid_df = invalid_df.unionByName(
            duplicate_loan_ids.withColumn("reject_reason", lit("duplicate_loan_id")),
            allowMissingColumns=True,
        )

    return valid_dedup, invalid_df


def validate_mtm_pnl(df: DataFrame) -> tuple[DataFrame, DataFrame]:
    """
    Validate mtm_pnl using DataFrame API only.
    Required non-empty; mtm_value_current_usd, pnl_usd parseable; dedupe by (file_date, book_id, desk_id, loan_id).
    """
    required_str = ["file_date", "book_id", "desk_id", "loan_id"]
    cond = lit(True)
    for c in required_str:
        cond = cond & (col(c).isNotNull() & (col(c) != ""))

    mtm_ok = col("mtm_value_current_usd").cast("double").isNotNull()
    pnl_ok = col("pnl_usd").cast("double").isNotNull()

    reject_reason = when(~cond, lit("missing_or_empty_required")).when(
        ~mtm_ok, lit("mtm_value_current_usd_invalid")
    ).when(~pnl_ok, lit("pnl_usd_invalid")).otherwise(lit(""))

    df_with_reason = df.withColumn("_reject_reason", reject_reason)
    valid_df = df_with_reason.filter(col("_reject_reason") == "").drop("_reject_reason")
    invalid_df = df_with_reason.filter(col("_reject_reason") != "").withColumnRenamed("_reject_reason", "reject_reason")

    w = Window.partitionBy("file_date", "book_id", "desk_id", "loan_id").orderBy("loan_id")
    valid_dedup = valid_df.withColumn("_rn", row_number().over(w)).filter(col("_rn") == 1).drop("_rn")
    dups = valid_df.withColumn("_rn", row_number().over(w)).filter(col("_rn") > 1).drop("_rn")
    if dups.count() > 0:
        invalid_df = invalid_df.unionByName(
            dups.withColumn("reject_reason", lit("duplicate_file_date_book_desk_loan")),
            allowMissingColumns=True,
        )

    return valid_dedup, invalid_df


def write_curated_simple(df: DataFrame, bucket: str, file_type: str, file_date: str) -> None:
    path = f"s3://{bucket}/equilend/{file_type}/file_date={file_date}"
    df.write.mode("overwrite").partitionBy("file_date").parquet(path)


def write_quarantine(df: DataFrame, bucket: str, file_type: str, file_date: str, filename: str) -> None:
    path = f"s3://{bucket}/quarantine/equilend/{file_type}/file_date={file_date}/{filename}.parquet"
    df.write.mode("overwrite").parquet(path)


def update_audit(
    table_name: str,
    pk: str,
    sk: str,
    status: str,
    record_count: int,
    reject_count: int,
    job_id: Optional[str] = None,
) -> None:
    if not table_name:
        return
    dynamodb = boto3.resource("dynamodb")
    table = dynamodb.Table(table_name)
    now = datetime.utcnow().strftime("%Y-%m-%dT%H:%M:%SZ")
    update_expr = "SET #st = :status, record_count = :rc, reject_count = :rej, processed_at = :ts"
    expr_names = {"#st": "status"}
    expr_values = {
        ":status": status,
        ":rc": record_count,
        ":rej": reject_count,
        ":ts": now,
    }
    if job_id:
        update_expr += ", job_id = :jid"
        expr_values[":jid"] = job_id
    expr_values[":pending"] = "PENDING"
    table.update_item(
        Key={"pk": pk, "sk": sk},
        UpdateExpression=update_expr,
        ConditionExpression="#st = :pending",
        ExpressionAttributeNames=expr_names,
        ExpressionAttributeValues=expr_values,
    )


def main() -> None:
    if file_type == "positions":
        schema = POSITIONS_STRING_SCHEMA
    elif file_type == "mtm_pnl":
        schema = MTM_PNL_STRING_SCHEMA
    else:
        raise ValueError(f"Unsupported file_type: {file_type}")

    raw = read_csv_with_bookmark(glueContext, input_path, schema)

    if file_type == "positions":
        valid_df, invalid_df = validate_positions(raw)
    else:
        valid_df, invalid_df = validate_mtm_pnl(raw)

    valid_count = valid_df.count()
    reject_count = invalid_df.count()

    if reject_count > 0:
        write_quarantine(invalid_df, quarantine_bucket, file_type, file_date, filename.replace(".csv", ""))

    if valid_count > 0:
        write_curated_simple(valid_df, curated_bucket, file_type, file_date)

    if reject_count > 0 and valid_count > 0:
        status = "PARTIAL"
    elif reject_count > 0 and valid_count == 0:
        status = "FAILED"
    else:
        status = "SUCCESS"

    try:
        update_audit(audit_table_name, pk, sk, status, valid_count, reject_count, job_id=None)
    except ClientError as e:
        if e.response["Error"]["Code"] != "ConditionalCheckFailedException":
            raise

    job.commit()


if __name__ == "__main__":
    main()
