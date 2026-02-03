package workflow.equilend.batch;

/**
 * Either a valid curated record or an invalid row with reject reason.
 */
public final class ValidatedRow<T> {

    private final T record;
    private final String rejectReason;

    private ValidatedRow(T record, String rejectReason) {
        this.record = record;
        this.rejectReason = rejectReason;
    }

    public static <T> ValidatedRow<T> valid(T record) {
        return new ValidatedRow<>(record, null);
    }

    public static <T> ValidatedRow<T> invalid(String rejectReason) {
        return new ValidatedRow<>(null, rejectReason);
    }

    public boolean isValid() { return rejectReason == null; }
    public T getRecord() { return record; }
    public String getRejectReason() { return rejectReason; }
}
