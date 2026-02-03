package workflow.equilend.eventing;

import java.time.Instant;

/**
 * Payload for file-arrival (S3 ObjectCreated:Complete → SNS → SQS).
 * Orchestrator consumes this to start the batch job.
 */
public final class FileArrivalEvent {

    private final String bucket;
    private final String key;
    private final long fileSize;
    private final Instant eventTime;
    private final String etag;

    public FileArrivalEvent(String bucket, String key, long fileSize, Instant eventTime, String etag) {
        this.bucket = bucket;
        this.key = key;
        this.fileSize = fileSize;
        this.eventTime = eventTime != null ? eventTime : Instant.now();
        this.etag = etag;
    }

    public String getBucket() { return bucket; }
    public String getKey() { return key; }
    public long getFileSize() { return fileSize; }
    public Instant getEventTime() { return eventTime; }
    public String getEtag() { return etag; }

    /** Full path for reading: bucket/key or basePath/key. */
    public String getInputPath(String basePath) {
        if (basePath == null || basePath.isEmpty()) return key;
        return basePath.endsWith("/") ? basePath + key : basePath + "/" + key;
    }

    public FileType getFileType() {
        return FileType.fromKey(key);
    }

    /** File date from filename (e.g. equilend_positions_20250201.csv → 20250201). */
    public String getFileDateFromKey() {
        if (key == null) return null;
        int lastSlash = key.lastIndexOf('/');
        String name = lastSlash >= 0 ? key.substring(lastSlash + 1) : key;
        // equilend_positions_20250201.csv or equilend_mtm_pnl_20250201.csv
        int u = name.lastIndexOf('_');
        int d = name.indexOf('.');
        if (u >= 0 && d > u) return name.substring(u + 1, d);
        return null;
    }
}
