"""
AWS Glue (PySpark) batch job: Equilend file processing — SQL STYLE.

Same behavior as glue_etl.py but validation and deduplication are implemented
entirely with Spark SQL (temp views + spark.sql). Use this for interview prep
when the interviewer prefers SQL-style PySpark.

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
from pyspark.sql.functions import lit
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
    from pyspark.sql.functions import col
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
    Validate positions using Spark SQL only.
    Required non-empty; quantity > 0; loan_value_usd >= 0; settle_date >= trade_date; dedupe by loan_id.
    """
    df.createOrReplaceTempView("_raw_positions")
    spark.sql("""
        CREATE OR REPLACE TEMP VIEW _positions_with_reason AS
        SELECT *,
            CASE
                WHEN (loan_id IS NULL OR TRIM(loan_id) = '')
                    OR (security_id IS NULL OR TRIM(security_id) = '')
                    OR (borrower_id IS NULL OR TRIM(borrower_id) = '')
                    OR (book_id IS NULL OR TRIM(book_id) = '')
                    OR (desk_id IS NULL OR TRIM(desk_id) = '')
                    OR (trade_date IS NULL OR TRIM(trade_date) = '')
                    OR (settle_date IS NULL OR TRIM(settle_date) = '')
                    OR (file_date IS NULL OR TRIM(file_date) = '')
                THEN 'missing_or_empty_required'
                WHEN CAST(quantity AS DOUBLE) IS NULL OR CAST(quantity AS DOUBLE) <= 0
                THEN 'quantity_invalid_or_not_positive'
                WHEN CAST(loan_value_usd AS DOUBLE) IS NULL OR CAST(loan_value_usd AS DOUBLE) < 0
                THEN 'loan_value_invalid_or_negative'
                WHEN to_date(trade_date, 'yyyy-MM-dd') IS NULL OR to_date(settle_date, 'yyyy-MM-dd') IS NULL
                    OR to_date(settle_date, 'yyyy-MM-dd') < to_date(trade_date, 'yyyy-MM-dd')
                THEN 'settle_date_before_trade_date'
                ELSE ''
            END AS _reject_reason
        FROM _raw_positions
    """)
    invalid_df = spark.sql("""
        SELECT * FROM _positions_with_reason WHERE _reject_reason != ''
    """).withColumnRenamed("_reject_reason", "reject_reason")

    valid_candidates = spark.sql("""
        SELECT * FROM _positions_with_reason WHERE _reject_reason = ''
    """).drop("_reject_reason")
    valid_candidates.createOrReplaceTempView("_valid_positions_candidates")

    valid_dedup = spark.sql("""
        SELECT * FROM (
            SELECT *, ROW_NUMBER() OVER (PARTITION BY loan_id ORDER BY loan_id) AS _rn
            FROM _valid_positions_candidates
        ) t
        WHERE _rn = 1
    """).drop("_rn")

    duplicate_loan_ids = spark.sql("""
        SELECT * FROM (
            SELECT *, ROW_NUMBER() OVER (PARTITION BY loan_id ORDER BY loan_id) AS _rn
            FROM _valid_positions_candidates
        ) t
        WHERE _rn > 1
    """).drop("_rn")

    if duplicate_loan_ids.count() > 0:
        invalid_df = invalid_df.unionByName(
            duplicate_loan_ids.withColumn("reject_reason", lit("duplicate_loan_id")),
            allowMissingColumns=True,
        )

    return valid_dedup, invalid_df


def validate_mtm_pnl(df: DataFrame) -> tuple[DataFrame, DataFrame]:
    """
    Validate mtm_pnl using Spark SQL only.
    Required non-empty; mtm_value_current_usd, pnl_usd parseable; dedupe by (file_date, book_id, desk_id, loan_id).
    """
    df.createOrReplaceTempView("_raw_mtm_pnl")
    spark.sql("""
        CREATE OR REPLACE TEMP VIEW _mtm_pnl_with_reason AS
        SELECT *,
            CASE
                WHEN (file_date IS NULL OR TRIM(file_date) = '')
                    OR (book_id IS NULL OR TRIM(book_id) = '')
                    OR (desk_id IS NULL OR TRIM(desk_id) = '')
                    OR (loan_id IS NULL OR TRIM(loan_id) = '')
                THEN 'missing_or_empty_required'
                WHEN CAST(mtm_value_current_usd AS DOUBLE) IS NULL
                THEN 'mtm_value_current_usd_invalid'
                WHEN CAST(pnl_usd AS DOUBLE) IS NULL
                THEN 'pnl_usd_invalid'
                ELSE ''
            END AS _reject_reason
        FROM _raw_mtm_pnl
    """)
    invalid_df = spark.sql("""
        SELECT * FROM _mtm_pnl_with_reason WHERE _reject_reason != ''
    """).withColumnRenamed("_reject_reason", "reject_reason")

    valid_candidates = spark.sql("""
        SELECT * FROM _mtm_pnl_with_reason WHERE _reject_reason = ''
    """).drop("_reject_reason")
    valid_candidates.createOrReplaceTempView("_valid_mtm_candidates")

    valid_dedup = spark.sql("""
        SELECT * FROM (
            SELECT *, ROW_NUMBER() OVER (PARTITION BY file_date, book_id, desk_id, loan_id ORDER BY loan_id) AS _rn
            FROM _valid_mtm_candidates
        ) t
        WHERE _rn = 1
    """).drop("_rn")

    dups = spark.sql("""
        SELECT * FROM (
            SELECT *, ROW_NUMBER() OVER (PARTITION BY file_date, book_id, desk_id, loan_id ORDER BY loan_id) AS _rn
            FROM _valid_mtm_candidates
        ) t
        WHERE _rn > 1
    """).drop("_rn")

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
