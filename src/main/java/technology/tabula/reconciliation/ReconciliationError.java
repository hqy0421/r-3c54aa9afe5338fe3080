package technology.tabula.reconciliation;

import java.math.BigDecimal;
import java.util.Locale;

/**
 * A single reconciliation problem, anchored to a page, a row within the table on
 * that page, and (when known) a currency.
 */
public final class ReconciliationError {

    public enum Kind {
        /** The amount cell could not be parsed as a decimal amount. */
        INVALID_AMOUNT,
        /** The currency cell was blank on a detail, subtotal, or total row. */
        INVALID_CURRENCY,
        /** A subtotal did not equal the details accumulated since the previous subtotal. */
        SUBTOTAL_MISMATCH,
        /** A total did not equal all details for its currency on the table. */
        TOTAL_MISMATCH,
        /** Details for a currency were never covered by a subtotal. */
        UNCLOSED_SEGMENT,
        /** A currency that appeared on the table has no closing total. */
        MISSING_TOTAL,
        /** A currency appeared again after its closing total. */
        CURRENCY_AFTER_TOTAL
    }

    private final Kind kind;
    private final int pageNumber;
    private final int rowNumber;
    private final String currency;
    private final String detail;

    public ReconciliationError(Kind kind, int pageNumber, int rowNumber, String currency, String detail) {
        this.kind = kind;
        this.pageNumber = pageNumber;
        this.rowNumber = rowNumber;
        this.currency = currency == null ? "" : currency;
        this.detail = detail == null ? "" : detail;
    }

    public Kind getKind() {
        return kind;
    }

    public int getPageNumber() {
        return pageNumber;
    }

    /**
     * @return the one-based row number within the extracted table
     */
    public int getRowNumber() {
        return rowNumber;
    }

    /**
     * @return the currency as printed, or the empty string when it was blank
     */
    public String getCurrency() {
        return currency;
    }

    public String getDetail() {
        return detail;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("page ").append(pageNumber)
          .append(", row ").append(rowNumber)
          .append(", currency ").append(currency.isEmpty() ? "<blank>" : currency)
          .append(": ").append(describe(kind));
        if (!detail.isEmpty()) {
            sb.append(' ').append(detail);
        }
        return sb.toString();
    }

    /**
     * Formats a multi-line, human-readable report of every error.
     */
    public static String formatAll(Iterable<ReconciliationError> errors) {
        StringBuilder sb = new StringBuilder();
        for (ReconciliationError error : errors) {
            if (sb.length() > 0) {
                sb.append(System.lineSeparator());
            }
            sb.append(error.toString());
        }
        return sb.toString();
    }

    private static String describe(Kind kind) {
        switch (kind) {
            case INVALID_AMOUNT:
                return "invalid amount";
            case INVALID_CURRENCY:
                return "missing currency";
            case SUBTOTAL_MISMATCH:
                return "subtotal does not match details since previous subtotal";
            case TOTAL_MISMATCH:
                return "total does not match all details for currency";
            case UNCLOSED_SEGMENT:
                return "details were not closed by a subtotal";
            case MISSING_TOTAL:
                return "currency has no closing total";
            case CURRENCY_AFTER_TOTAL:
                return "currency appears again after its total";
            default:
                return kind.name().toLowerCase(Locale.ROOT);
        }
    }

    static String amount(BigDecimal value) {
        return value == null ? "" : value.toPlainString();
    }
}
