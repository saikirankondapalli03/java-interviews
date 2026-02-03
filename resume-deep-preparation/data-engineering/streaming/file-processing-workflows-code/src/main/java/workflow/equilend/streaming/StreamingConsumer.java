package workflow.equilend.streaming;

import workflow.equilend.batch.MtmPnlRecord;
import workflow.equilend.batch.PositionRecord;
import workflow.equilend.eventbus.CuratedRecordEvent;
import workflow.equilend.reportingstore.ReportingStore;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Consumes curated events from the event bus; aggregates and upserts into reporting store.
 * In production: Spark Streaming consumes from MSK, aggregates, MERGE into Snowflake.
 */
public final class StreamingConsumer implements workflow.equilend.eventbus.EventBus.CuratedRecordConsumer {

    private final ReportingStore reportingStore;
    private final Map<String, List<PositionRecord>> positionsBufferByDate = new ConcurrentHashMap<>();
    private final Map<String, List<MtmPnlRecord>> pnlBufferByDate = new ConcurrentHashMap<>();

    public StreamingConsumer(ReportingStore reportingStore) {
        this.reportingStore = reportingStore;
    }

    @Override
    public void onEvent(CuratedRecordEvent event) {
        if (event.getType() == CuratedRecordEvent.Type.POSITIONS) {
            PositionRecord r = (PositionRecord) event.getPayload();
            String fd = event.getFileDate();
            positionsBufferByDate.computeIfAbsent(fd, k -> new ArrayList<>()).add(r);
        } else if (event.getType() == CuratedRecordEvent.Type.MTM_PNL) {
            MtmPnlRecord r = (MtmPnlRecord) event.getPayload();
            String fd = event.getFileDate();
            pnlBufferByDate.computeIfAbsent(fd, k -> new ArrayList<>()).add(r);
        }
    }

    /**
     * Flush buffers for a given file_date: upsert positions, compute exposure by borrower, upsert P&L.
     * In production this would be triggered per micro-batch or on watermark.
     */
    public void flush(String fileDate) {
        List<PositionRecord> positions = positionsBufferByDate.remove(fileDate);
        if (positions != null && !positions.isEmpty()) {
            reportingStore.upsertPositions(fileDate, positions);
            List<ReportingStore.ExposureByBorrower> exposure = positions.stream()
                    .collect(Collectors.groupingBy(PositionRecord::getBorrowerId))
                    .entrySet().stream()
                    .map(e -> {
                        long count = e.getValue().size();
                        BigDecimal total = e.getValue().stream()
                                .map(PositionRecord::getLoanValueUsd)
                                .reduce(BigDecimal.ZERO, BigDecimal::add);
                        return new ReportingStore.ExposureByBorrower(fileDate, e.getKey(), count, total);
                    })
                    .collect(Collectors.toList());
            reportingStore.upsertExposureByBorrower(fileDate, exposure);
        }

        List<MtmPnlRecord> pnl = pnlBufferByDate.remove(fileDate);
        if (pnl != null && !pnl.isEmpty()) {
            reportingStore.upsertDailyPnl(fileDate, pnl);
        }
    }

    /** Flush all buffered dates (e.g. at end of batch). */
    public void flushAll() {
        for (String fd : new ArrayList<>(positionsBufferByDate.keySet())) {
            flush(fd);
        }
        for (String fd : new ArrayList<>(pnlBufferByDate.keySet())) {
            flush(fd);
        }
    }
}
