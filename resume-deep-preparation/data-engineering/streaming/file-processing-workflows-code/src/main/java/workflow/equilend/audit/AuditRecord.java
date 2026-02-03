package workflow.equilend.audit;

import java.time.Instant;

/**
 * One row per file in the audit table (DynamoDB or RDS).
 * PK = file_type#file_date, SK = s3_key (or equivalent).
 */
public final class AuditRecord {

    private final String idempotencyKey;
    private final String fileType;
    private final String fileDate;
    private final String s3Uri;
    private final AuditStatus status;
    private final long recordCount;
    private final long rejectCount;
    private final Instant processedAt;
    private final String jobId;

    public AuditRecord(String idempotencyKey, String fileType, String fileDate, String s3Uri,
                       AuditStatus status, long recordCount, long rejectCount,
                       Instant processedAt, String jobId) {
        this.idempotencyKey = idempotencyKey;
        this.fileType = fileType;
        this.fileDate = fileDate;
        this.s3Uri = s3Uri;
        this.status = status;
        this.recordCount = recordCount;
        this.rejectCount = rejectCount;
        this.processedAt = processedAt;
        this.jobId = jobId;
    }

    public String getIdempotencyKey() { return idempotencyKey; }
    public String getFileType() { return fileType; }
    public String getFileDate() { return fileDate; }
    public String getS3Uri() { return s3Uri; }
    public AuditStatus getStatus() { return status; }
    public long getRecordCount() { return recordCount; }
    public long getRejectCount() { return rejectCount; }
    public Instant getProcessedAt() { return processedAt; }
    public String getJobId() { return jobId; }

    public static String buildIdempotencyKey(String sourceSystem, String fileType, String fileDate, String fileName) {
        return sourceSystem + "|" + fileType + "|" + fileDate + "|" + fileName;
    }
}
