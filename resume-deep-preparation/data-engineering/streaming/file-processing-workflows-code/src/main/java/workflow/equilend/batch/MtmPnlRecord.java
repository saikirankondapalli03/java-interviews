package workflow.equilend.batch;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One row from Equilend MTM P&L file (curated).
 */
public final class MtmPnlRecord {

    private final LocalDate fileDate;
    private final String bookId;
    private final String deskId;
    private final String loanId;
    private final BigDecimal mtmValuePrevUsd;
    private final BigDecimal mtmValueCurrentUsd;
    private final BigDecimal pnlUsd;
    private final BigDecimal rebateIncomeUsd;
    private final BigDecimal feeIncomeUsd;
    private final String sourceSystem;

    public MtmPnlRecord(LocalDate fileDate, String bookId, String deskId, String loanId,
                        BigDecimal mtmValuePrevUsd, BigDecimal mtmValueCurrentUsd, BigDecimal pnlUsd,
                        BigDecimal rebateIncomeUsd, BigDecimal feeIncomeUsd, String sourceSystem) {
        this.fileDate = fileDate;
        this.bookId = bookId;
        this.deskId = deskId;
        this.loanId = loanId;
        this.mtmValuePrevUsd = mtmValuePrevUsd;
        this.mtmValueCurrentUsd = mtmValueCurrentUsd;
        this.pnlUsd = pnlUsd;
        this.rebateIncomeUsd = rebateIncomeUsd;
        this.feeIncomeUsd = feeIncomeUsd;
        this.sourceSystem = sourceSystem;
    }

    public LocalDate getFileDate() { return fileDate; }
    public String getBookId() { return bookId; }
    public String getDeskId() { return deskId; }
    public String getLoanId() { return loanId; }
    public BigDecimal getMtmValuePrevUsd() { return mtmValuePrevUsd; }
    public BigDecimal getMtmValueCurrentUsd() { return mtmValueCurrentUsd; }
    public BigDecimal getPnlUsd() { return pnlUsd; }
    public BigDecimal getRebateIncomeUsd() { return rebateIncomeUsd; }
    public BigDecimal getFeeIncomeUsd() { return feeIncomeUsd; }
    public String getSourceSystem() { return sourceSystem; }
}
