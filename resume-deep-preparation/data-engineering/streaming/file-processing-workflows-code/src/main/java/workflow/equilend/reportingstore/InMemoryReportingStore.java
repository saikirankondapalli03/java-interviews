package workflow.equilend.reportingstore;

import workflow.equilend.batch.MtmPnlRecord;
import workflow.equilend.batch.PositionRecord;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * In-memory reporting store for local/demo. Production: Snowflake (MERGE by file_date).
 */
public final class InMemoryReportingStore implements ReportingStore {

    private final Map<String, List<DailyPositionView>> positionsByDate = new ConcurrentHashMap<>();
    private final Map<String, List<ExposureByBorrower>> exposureByDate = new ConcurrentHashMap<>();
    private final Map<String, List<DailyPnlView>> pnlByDate = new ConcurrentHashMap<>();

    @Override
    public void upsertPositions(String fileDate, List<PositionRecord> records) {
        List<DailyPositionView> views = records.stream()
                .map(r -> new DailyPositionView(
                        r.getLoanId(), r.getSecurityId(), r.getBorrowerId(), r.getBookId(), r.getDeskId(),
                        fileDate, r.getQuantity(), r.getLoanValueUsd()))
                .collect(Collectors.toList());
        positionsByDate.put(fileDate, new ArrayList<>(views));
    }

    @Override
    public void upsertExposureByBorrower(String fileDate, List<ExposureByBorrower> aggregates) {
        exposureByDate.put(fileDate, new ArrayList<>(aggregates));
    }

    @Override
    public void upsertDailyPnl(String fileDate, List<MtmPnlRecord> records) {
        List<DailyPnlView> views = records.stream()
                .map(r -> new DailyPnlView(
                        fileDate, r.getBookId(), r.getDeskId(), r.getLoanId(), r.getPnlUsd()))
                .collect(Collectors.toList());
        pnlByDate.put(fileDate, new ArrayList<>(views));
    }

    @Override
    public List<DailyPositionView> getDailyPositions(String fileDate) {
        return new ArrayList<>(positionsByDate.getOrDefault(fileDate, List.of()));
    }

    @Override
    public List<ExposureByBorrower> getExposureByBorrower(String fileDate) {
        return new ArrayList<>(exposureByDate.getOrDefault(fileDate, List.of()));
    }

    @Override
    public List<DailyPnlView> getDailyPnl(String fileDate) {
        return new ArrayList<>(pnlByDate.getOrDefault(fileDate, List.of()));
    }
}
