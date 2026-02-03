package workflow.equilend.batch;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One row from Equilend positions file (curated).
 */
public final class PositionRecord {

    private final String loanId;
    private final LocalDate tradeDate;
    private final LocalDate settleDate;
    private final String securityId;
    private final String securityName;
    private final String borrowerId;
    private final String borrowerName;
    private final BigDecimal quantity;
    private final BigDecimal loanValueUsd;
    private final BigDecimal rebateRateBps;
    private final BigDecimal feeRateBps;
    private final String collateralType;
    private final BigDecimal collateralValueUsd;
    private final String bookId;
    private final String deskId;
    private final String sourceSystem;
    private final LocalDate fileDate;

    public PositionRecord(String loanId, LocalDate tradeDate, LocalDate settleDate,
                          String securityId, String securityName, String borrowerId, String borrowerName,
                          BigDecimal quantity, BigDecimal loanValueUsd, BigDecimal rebateRateBps, BigDecimal feeRateBps,
                          String collateralType, BigDecimal collateralValueUsd, String bookId, String deskId,
                          String sourceSystem, LocalDate fileDate) {
        this.loanId = loanId;
        this.tradeDate = tradeDate;
        this.settleDate = settleDate;
        this.securityId = securityId;
        this.securityName = securityName;
        this.borrowerId = borrowerId;
        this.borrowerName = borrowerName;
        this.quantity = quantity;
        this.loanValueUsd = loanValueUsd;
        this.rebateRateBps = rebateRateBps;
        this.feeRateBps = feeRateBps;
        this.collateralType = collateralType;
        this.collateralValueUsd = collateralValueUsd;
        this.bookId = bookId;
        this.deskId = deskId;
        this.sourceSystem = sourceSystem;
        this.fileDate = fileDate;
    }

    public String getLoanId() { return loanId; }
    public LocalDate getTradeDate() { return tradeDate; }
    public LocalDate getSettleDate() { return settleDate; }
    public String getSecurityId() { return securityId; }
    public String getSecurityName() { return securityName; }
    public String getBorrowerId() { return borrowerId; }
    public String getBorrowerName() { return borrowerName; }
    public BigDecimal getQuantity() { return quantity; }
    public BigDecimal getLoanValueUsd() { return loanValueUsd; }
    public BigDecimal getRebateRateBps() { return rebateRateBps; }
    public BigDecimal getFeeRateBps() { return feeRateBps; }
    public String getCollateralType() { return collateralType; }
    public BigDecimal getCollateralValueUsd() { return collateralValueUsd; }
    public String getBookId() { return bookId; }
    public String getDeskId() { return deskId; }
    public String getSourceSystem() { return sourceSystem; }
    public LocalDate getFileDate() { return fileDate; }
}
