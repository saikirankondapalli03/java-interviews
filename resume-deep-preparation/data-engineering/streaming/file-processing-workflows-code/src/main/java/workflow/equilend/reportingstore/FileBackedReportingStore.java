package workflow.equilend.reportingstore;

import com.fasterxml.jackson.databind.ObjectMapper;
import workflow.equilend.batch.MtmPnlRecord;
import workflow.equilend.batch.PositionRecord;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Reporting store that persists to JSON files under a base path.
 * PipelineRunner writes here; WorkflowApplication can load from the same path.
 */
public final class FileBackedReportingStore implements ReportingStore {

    private final String basePath;
    private final ObjectMapper mapper = new ObjectMapper();

    public FileBackedReportingStore(String basePath) {
        this.basePath = basePath;
    }

    @Override
    public void upsertPositions(String fileDate, List<PositionRecord> records) {
        List<DailyPositionView> views = records.stream()
                .map(r -> new DailyPositionView(
                        r.getLoanId(), r.getSecurityId(), r.getBorrowerId(), r.getBookId(), r.getDeskId(),
                        fileDate, r.getQuantity(), r.getLoanValueUsd()))
                .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
        write("daily-positions", fileDate, views);
    }

    @Override
    public void upsertExposureByBorrower(String fileDate, List<ExposureByBorrower> aggregates) {
        write("exposure-by-borrower", fileDate, new ArrayList<>(aggregates));
    }

    @Override
    public void upsertDailyPnl(String fileDate, List<MtmPnlRecord> records) {
        List<DailyPnlView> views = records.stream()
                .map(r -> new DailyPnlView(
                        fileDate, r.getBookId(), r.getDeskId(), r.getLoanId(), r.getPnlUsd()))
                .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
        write("daily-pnl", fileDate, views);
    }

    @Override
    public List<DailyPositionView> getDailyPositions(String fileDate) {
        return readList("daily-positions", fileDate, DailyPositionView.class);
    }

    @Override
    public List<ExposureByBorrower> getExposureByBorrower(String fileDate) {
        return readList("exposure-by-borrower", fileDate, ExposureByBorrower.class);
    }

    @Override
    public List<DailyPnlView> getDailyPnl(String fileDate) {
        return readList("daily-pnl", fileDate, DailyPnlView.class);
    }

    private void write(String reportType, String fileDate, List<?> data) {
        try {
            Path dir = Paths.get(basePath, reportType);
            Files.createDirectories(dir);
            Path file = dir.resolve(fileDate + ".json");
            mapper.writerWithDefaultPrettyPrinter().writeValue(file.toFile(), data);
        } catch (IOException e) {
            throw new RuntimeException("Failed to write " + reportType + "/" + fileDate, e);
        }
    }

    private <T> List<T> readList(String reportType, String fileDate, Class<T> elementType) {
        try {
            Path file = Paths.get(basePath, reportType, fileDate + ".json");
            if (!Files.isRegularFile(file)) return new ArrayList<>();
            return mapper.readValue(file.toFile(),
                    mapper.getTypeFactory().constructCollectionType(List.class, elementType));
        } catch (IOException e) {
            return new ArrayList<>();
        }
    }
}
