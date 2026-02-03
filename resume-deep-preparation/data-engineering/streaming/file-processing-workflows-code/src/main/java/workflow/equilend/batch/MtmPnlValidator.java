package workflow.equilend.batch;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.HashSet;
import java.util.Set;

/**
 * Validates one MTM P&L row. No duplicate (file_date, book_id, desk_id, loan_id).
 */
public final class MtmPnlValidator {

    private final Set<String> seenKeys = new HashSet<>();

    public ValidatedRow<MtmPnlRecord> validate(String[] values) {
        if (values == null || values.length < 10) {
            return ValidatedRow.invalid("INSUFFICIENT_COLUMNS");
        }
        String fileDateStr = get(values, 0);
        String bookId = get(values, 1);
        String deskId = get(values, 2);
        String loanId = get(values, 3);

        if (blank(fileDateStr)) return ValidatedRow.invalid("file_date_EMPTY");
        if (blank(bookId)) return ValidatedRow.invalid("book_id_EMPTY");
        if (blank(deskId)) return ValidatedRow.invalid("desk_id_EMPTY");
        if (blank(loanId)) return ValidatedRow.invalid("loan_id_EMPTY");

        String key = fileDateStr + "|" + bookId + "|" + deskId + "|" + loanId;
        if (seenKeys.contains(key)) return ValidatedRow.invalid("DUPLICATE_file_date_book_desk_loan");
        seenKeys.add(key);

        LocalDate fileDate = parseDate(fileDateStr);
        if (fileDate == null) return ValidatedRow.invalid("file_date_INVALID");

        BigDecimal mtmCurrent = parseDecimal(get(values, 5));
        BigDecimal pnlUsd = parseDecimal(get(values, 6));
        if (mtmCurrent == null) return ValidatedRow.invalid("mtm_value_current_usd_INVALID");
        if (pnlUsd == null) return ValidatedRow.invalid("pnl_usd_INVALID");

        BigDecimal mtmPrev = parseDecimal(get(values, 4));
        BigDecimal rebateIncome = parseDecimal(get(values, 7));
        BigDecimal feeIncome = parseDecimal(get(values, 8));
        if (mtmPrev == null) mtmPrev = BigDecimal.ZERO;
        if (rebateIncome == null) rebateIncome = BigDecimal.ZERO;
        if (feeIncome == null) feeIncome = BigDecimal.ZERO;

        MtmPnlRecord record = new MtmPnlRecord(
                fileDate, bookId, deskId, loanId,
                mtmPrev, mtmCurrent, pnlUsd, rebateIncome, feeIncome,
                get(values, 9));
        return ValidatedRow.valid(record);
    }

    public void reset() {
        seenKeys.clear();
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
