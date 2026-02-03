package workflow.equilend.batch;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.HashSet;
import java.util.Set;

/**
 * Validates one positions row: schema + business rules.
 * Invalid rows go to quarantine with reject reason.
 */
public final class PositionValidator {

    private final Set<String> seenLoanIds = new HashSet<>();

    public ValidatedRow<PositionRecord> validate(String[] headers, String[] values, String fileDateStr) {
        if (values == null || values.length < 17) {
            return ValidatedRow.invalid("INSUFFICIENT_COLUMNS");
        }
        String loanId = get(values, 0);
        String tradeDateStr = get(values, 1);
        String settleDateStr = get(values, 2);
        String securityId = get(values, 3);
        String borrowerId = get(values, 5);
        String quantityStr = get(values, 7);
        String loanValueStr = get(values, 8);
        String bookId = get(values, 13);
        String deskId = get(values, 14);

        if (blank(loanId)) return ValidatedRow.invalid("loan_id_EMPTY");
        if (seenLoanIds.contains(loanId)) return ValidatedRow.invalid("loan_id_DUPLICATE");
        if (blank(securityId)) return ValidatedRow.invalid("security_id_EMPTY");
        if (blank(borrowerId)) return ValidatedRow.invalid("borrower_id_EMPTY");
        if (blank(bookId)) return ValidatedRow.invalid("book_id_EMPTY");
        if (blank(deskId)) return ValidatedRow.invalid("desk_id_EMPTY");

        LocalDate tradeDate = parseDate(tradeDateStr);
        if (tradeDate == null) return ValidatedRow.invalid("trade_date_INVALID");
        LocalDate settleDate = parseDate(settleDateStr);
        if (settleDate == null) return ValidatedRow.invalid("settle_date_INVALID");
        if (settleDate.isBefore(tradeDate)) return ValidatedRow.invalid("settle_date_BEFORE_trade_date");

        BigDecimal quantity = parseDecimal(quantityStr);
        if (quantity == null || quantity.compareTo(BigDecimal.ZERO) <= 0) return ValidatedRow.invalid("quantity_INVALID_OR_NOT_POSITIVE");
        BigDecimal loanValueUsd = parseDecimal(loanValueStr);
        if (loanValueUsd == null || loanValueUsd.compareTo(BigDecimal.ZERO) < 0) return ValidatedRow.invalid("loan_value_usd_INVALID_OR_NEGATIVE");

        LocalDate fileDate = parseDate(fileDateStr);
        if (fileDate == null) fileDate = tradeDate;

        seenLoanIds.add(loanId);

        BigDecimal rebateRateBps = parseDecimal(get(values, 9));
        BigDecimal feeRateBps = parseDecimal(get(values, 10));
        BigDecimal collateralValueUsd = parseDecimal(get(values, 12));
        if (rebateRateBps == null) rebateRateBps = BigDecimal.ZERO;
        if (collateralValueUsd == null) collateralValueUsd = BigDecimal.ZERO;

        PositionRecord record = new PositionRecord(
                loanId, tradeDate, settleDate,
                securityId, get(values, 4), borrowerId, get(values, 6),
                quantity, loanValueUsd, rebateRateBps, feeRateBps,
                get(values, 11), collateralValueUsd, bookId, deskId,
                get(values, 15), fileDate);
        return ValidatedRow.valid(record);
    }

    public void reset() {
        seenLoanIds.clear();
    }

    private static String get(String[] arr, int i) {
        return i < arr.length ? (arr[i] == null ? "" : arr[i].trim()) : "";
    }

    private static boolean blank(String s) {
        return s == null || s.trim().isEmpty();
    }

    private static LocalDate parseDate(String s) {
        if (s == null || s.trim().isEmpty()) return null;
        try {
            return LocalDate.parse(s.trim());
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static BigDecimal parseDecimal(String s) {
        if (s == null || s.trim().isEmpty()) return null;
        try {
            return new BigDecimal(s.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
