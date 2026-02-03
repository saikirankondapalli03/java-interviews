package workflow.equilend.reportingstore;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import workflow.equilend.batch.MtmPnlRecord;
import workflow.equilend.batch.PositionRecord;

import java.util.List;

/**
 * Reporting store (Snowflake in production). API queries this for Daily Positions, Exposure by Borrower, Daily P&L.
 */
public interface ReportingStore {

    void upsertPositions(String fileDate, List<PositionRecord> records);

    void upsertExposureByBorrower(String fileDate, List<ExposureByBorrower> aggregates);

    void upsertDailyPnl(String fileDate, List<MtmPnlRecord> records);

    List<DailyPositionView> getDailyPositions(String fileDate);

    List<ExposureByBorrower> getExposureByBorrower(String fileDate);

    List<DailyPnlView> getDailyPnl(String fileDate);

    /** One row for Daily Positions report. */
    final class DailyPositionView {
        private final String loanId;
        private final String securityId;
        private final String borrowerId;
        private final String bookId;
        private final String deskId;
        private final String fileDate;
        private final Number quantity;
        private final Number loanValueUsd;

        @JsonCreator
        public DailyPositionView(@JsonProperty("loanId") String loanId, @JsonProperty("securityId") String securityId, @JsonProperty("borrowerId") String borrowerId, @JsonProperty("bookId") String bookId, @JsonProperty("deskId") String deskId, @JsonProperty("fileDate") String fileDate, @JsonProperty("quantity") Number quantity, @JsonProperty("loanValueUsd") Number loanValueUsd) {
            this.loanId = loanId;
            this.securityId = securityId;
            this.borrowerId = borrowerId;
            this.bookId = bookId;
            this.deskId = deskId;
            this.fileDate = fileDate;
            this.quantity = quantity;
            this.loanValueUsd = loanValueUsd;
        }

        public String getLoanId() { return loanId; }
        public String getSecurityId() { return securityId; }
        public String getBorrowerId() { return borrowerId; }
        public String getBookId() { return bookId; }
        public String getDeskId() { return deskId; }
        public String getFileDate() { return fileDate; }
        public Number getQuantity() { return quantity; }
        public Number getLoanValueUsd() { return loanValueUsd; }
    }

    /** Exposure by borrower (aggregate). */
    final class ExposureByBorrower {
        private final String fileDate;
        private final String borrowerId;
        private final long loanCount;
        private final Number totalLoanValueUsd;

        @JsonCreator
        public ExposureByBorrower(@JsonProperty("fileDate") String fileDate, @JsonProperty("borrowerId") String borrowerId, @JsonProperty("loanCount") long loanCount, @JsonProperty("totalLoanValueUsd") Number totalLoanValueUsd) {
            this.fileDate = fileDate;
            this.borrowerId = borrowerId;
            this.loanCount = loanCount;
            this.totalLoanValueUsd = totalLoanValueUsd;
        }

        public String getFileDate() { return fileDate; }
        public String getBorrowerId() { return borrowerId; }
        public long getLoanCount() { return loanCount; }
        public Number getTotalLoanValueUsd() { return totalLoanValueUsd; }
    }

    /** Daily P&L row. */
    final class DailyPnlView {
        private final String fileDate;
        private final String bookId;
        private final String deskId;
        private final String loanId;
        private final Number pnlUsd;

        @JsonCreator
        public DailyPnlView(@JsonProperty("fileDate") String fileDate, @JsonProperty("bookId") String bookId, @JsonProperty("deskId") String deskId, @JsonProperty("loanId") String loanId, @JsonProperty("pnlUsd") Number pnlUsd) {
            this.fileDate = fileDate;
            this.bookId = bookId;
            this.deskId = deskId;
            this.loanId = loanId;
            this.pnlUsd = pnlUsd;
        }

        public String getFileDate() { return fileDate; }
        public String getBookId() { return bookId; }
        public String getDeskId() { return deskId; }
        public String getLoanId() { return loanId; }
        public Number getPnlUsd() { return pnlUsd; }
    }
}
