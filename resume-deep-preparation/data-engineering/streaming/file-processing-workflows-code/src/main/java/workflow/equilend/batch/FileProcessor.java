package workflow.equilend.batch;

import workflow.equilend.eventing.FileType;
import workflow.equilend.eventbus.CuratedRecordEvent;
import workflow.equilend.eventbus.EventBus;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Batch file processor: read CSV from path, validate, write valid rows to curated + event bus,
 * invalid to quarantine; return record/reject counts. (Glue/PySpark equivalent in production.)
 */
public final class FileProcessor {

    private final String curatedBasePath;
    private final String quarantineBasePath;
    private final EventBus eventBus;

    public FileProcessor(String curatedBasePath, String quarantineBasePath, EventBus eventBus) {
        this.curatedBasePath = curatedBasePath;
        this.quarantineBasePath = quarantineBasePath;
        this.eventBus = eventBus;
    }

    public ProcessingResult process(String inputPath, FileType fileType, String fileDate) throws IOException {
        Path path = Paths.get(inputPath);
        if (!Files.isRegularFile(path)) {
            throw new IOException("Not a file: " + inputPath);
        }

        if (fileType == FileType.POSITIONS) {
            return processPositions(path, fileDate);
        } else if (fileType == FileType.MTM_PNL) {
            return processMtmPnl(path, fileDate);
        } else {
            throw new IllegalArgumentException("Unsupported file type: " + fileType);
        }
    }

    private ProcessingResult processPositions(Path path, String fileDate) throws IOException {
        PositionValidator validator = new PositionValidator();
        List<PositionRecord> valid = new ArrayList<>();
        List<String> quarantineLines = new ArrayList<>();

        try (BufferedReader reader = Files.newBufferedReader(path)) {
            String headerLine = reader.readLine();
            if (headerLine == null) return new ProcessingResult(0, 0);
            String[] headers = parseCsvLine(headerLine);
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                String[] values = parseCsvLine(line);
                ValidatedRow<PositionRecord> row = validator.validate(headers, values, fileDate);
                if (row.isValid()) {
                    valid.add(row.getRecord());
                    eventBus.publish(CuratedRecordEvent.positions(row.getRecord(), fileDate));
                } else {
                    quarantineLines.add(csvEscape(line) + "," + csvEscape(row.getRejectReason()));
                }
            }
        }

        writeCuratedPositions(valid, fileDate);
        writeQuarantine(path.getFileName().toString(), fileDate, "positions", quarantineLines);
        return new ProcessingResult(valid.size(), quarantineLines.size());
    }

    private ProcessingResult processMtmPnl(Path path, String fileDate) throws IOException {
        MtmPnlValidator validator = new MtmPnlValidator();
        List<MtmPnlRecord> valid = new ArrayList<>();
        List<String> quarantineLines = new ArrayList<>();

        try (BufferedReader reader = Files.newBufferedReader(path)) {
            String headerLine = reader.readLine();
            if (headerLine == null) return new ProcessingResult(0, 0);
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                String[] values = parseCsvLine(line);
                ValidatedRow<MtmPnlRecord> row = validator.validate(values);
                if (row.isValid()) {
                    valid.add(row.getRecord());
                    eventBus.publish(CuratedRecordEvent.mtmPnl(row.getRecord(), fileDate));
                } else {
                    quarantineLines.add(csvEscape(line) + "," + csvEscape(row.getRejectReason()));
                }
            }
        }

        writeCuratedMtmPnl(valid, fileDate);
        writeQuarantine(path.getFileName().toString(), fileDate, "mtm_pnl", quarantineLines);
        return new ProcessingResult(valid.size(), quarantineLines.size());
    }

    private void writeCuratedPositions(List<PositionRecord> records, String fileDate) throws IOException {
        Path dir = Paths.get(curatedBasePath, "equilend", "positions", "file_date=" + fileDate);
        Files.createDirectories(dir);
        Path out = dir.resolve("data.csv");
        List<String> lines = new ArrayList<>();
        lines.add("loan_id,trade_date,settle_date,security_id,security_name,borrower_id,borrower_name,quantity,loan_value_usd,rebate_rate_bps,fee_rate_bps,collateral_type,collateral_value_usd,book_id,desk_id,source_system,file_date");
        for (PositionRecord r : records) {
            lines.add(csvEscape(r.getLoanId()) + "," + r.getTradeDate() + "," + r.getSettleDate() + ","
                    + csvEscape(r.getSecurityId()) + "," + csvEscape(r.getSecurityName()) + ","
                    + csvEscape(r.getBorrowerId()) + "," + csvEscape(r.getBorrowerName()) + ","
                    + r.getQuantity() + "," + r.getLoanValueUsd() + "," + r.getRebateRateBps() + "," + (r.getFeeRateBps() != null ? r.getFeeRateBps() : "") + ","
                    + csvEscape(r.getCollateralType()) + "," + r.getCollateralValueUsd() + ","
                    + csvEscape(r.getBookId()) + "," + csvEscape(r.getDeskId()) + ","
                    + csvEscape(r.getSourceSystem()) + "," + r.getFileDate());
        }
        Files.write(out, lines);
    }

    private void writeCuratedMtmPnl(List<MtmPnlRecord> records, String fileDate) throws IOException {
        Path dir = Paths.get(curatedBasePath, "equilend", "mtm_pnl", "file_date=" + fileDate);
        Files.createDirectories(dir);
        Path out = dir.resolve("data.csv");
        List<String> lines = new ArrayList<>();
        lines.add("file_date,book_id,desk_id,loan_id,mtm_value_prev_usd,mtm_value_current_usd,pnl_usd,rebate_income_usd,fee_income_usd,source_system");
        for (MtmPnlRecord r : records) {
            lines.add(r.getFileDate() + "," + csvEscape(r.getBookId()) + "," + csvEscape(r.getDeskId()) + ","
                    + csvEscape(r.getLoanId()) + "," + r.getMtmValuePrevUsd() + "," + r.getMtmValueCurrentUsd() + ","
                    + r.getPnlUsd() + "," + r.getRebateIncomeUsd() + "," + r.getFeeIncomeUsd() + ","
                    + csvEscape(r.getSourceSystem()));
        }
        Files.write(out, lines);
    }

    private void writeQuarantine(String originalFileName, String fileDate, String fileType, List<String> lines) throws IOException {
        if (lines.isEmpty()) return;
        Path dir = Paths.get(quarantineBasePath, "equilend", fileType, fileDate);
        Files.createDirectories(dir);
        Path out = dir.resolve(originalFileName + ".rejects.csv");
        List<String> withHeader = new ArrayList<>();
        withHeader.add("original_row,reject_reason");
        withHeader.addAll(lines);
        Files.write(out, withHeader);
    }

    private static String[] parseCsvLine(String line) {
        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if ((c == ',' && !inQuotes) || c == '\n' || c == '\r') {
                result.add(current.toString().trim());
                current = new StringBuilder();
                if (c != ',') continue;
            } else {
                current.append(c);
            }
        }
        result.add(current.toString().trim());
        return result.toArray(new String[0]);
    }

    private static String csvEscape(String s) {
        if (s == null) return "";
        if (s.contains(",") || s.contains("\"")) return "\"" + s.replace("\"", "\"\"") + "\"";
        return s;
    }
}
