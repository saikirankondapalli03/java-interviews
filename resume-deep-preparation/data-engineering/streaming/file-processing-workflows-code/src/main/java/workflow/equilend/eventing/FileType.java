package workflow.equilend.eventing;

/**
 * Equilend file type derived from S3 key prefix (positions/ or mtm_pnl/).
 */
public enum FileType {
    POSITIONS("positions"),
    MTM_PNL("mtm_pnl");

    private final String prefix;

    FileType(String prefix) {
        this.prefix = prefix;
    }

    public String getPrefix() {
        return prefix;
    }

    public static FileType fromKey(String s3Key) {
        if (s3Key == null) return null;
        if (s3Key.startsWith("positions/")) return POSITIONS;
        if (s3Key.startsWith("mtm_pnl/")) return MTM_PNL;
        return null;
    }
}
