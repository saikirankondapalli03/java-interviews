package workflow.equilend.batch;

/**
 * Result of processing one file: record counts and optional error.
 */
public final class ProcessingResult {

    private final long recordCount;
    private final long rejectCount;

    public ProcessingResult(long recordCount, long rejectCount) {
        this.recordCount = recordCount;
        this.rejectCount = rejectCount;
    }

    public long getRecordCount() { return recordCount; }
    public long getRejectCount() { return rejectCount; }
}
