package workflow.equilend.eventbus;

import workflow.equilend.batch.MtmPnlRecord;
import workflow.equilend.batch.PositionRecord;

/**
 * Event published to the event bus (Kafka/MSK in prod) when a curated row is written.
 * Consumer (streaming) aggregates and sinks to reporting store.
 */
public final class CuratedRecordEvent {

    public enum Type { POSITIONS, MTM_PNL }

    private final Type type;
    private final String fileDate;
    private final Object payload;

    public CuratedRecordEvent(Type type, String fileDate, Object payload) {
        this.type = type;
        this.fileDate = fileDate;
        this.payload = payload;
    }

    public static CuratedRecordEvent positions(PositionRecord record, String fileDate) {
        return new CuratedRecordEvent(Type.POSITIONS, fileDate, record);
    }

    public static CuratedRecordEvent mtmPnl(MtmPnlRecord record, String fileDate) {
        return new CuratedRecordEvent(Type.MTM_PNL, fileDate, record);
    }

    public Type getType() { return type; }
    public String getFileDate() { return fileDate; }
    public Object getPayload() { return payload; }
}
