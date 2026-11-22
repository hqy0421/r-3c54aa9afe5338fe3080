package technology.tabula.reconciliation;

import java.util.Locale;

/**
 * Configuration for CSV reconciliation mode.
 *
 * <p>Reconciliation mode is meant for statements whose rows are either detail
 * rows, currency subtotals, or currency grand totals. The three column indexes
 * (all zero-based) and the marker strings that identify the row kinds are
 * supplied by the caller. The currency and amount columns are read for every
 * non-blank row.</p>
 *
 * <p>{@code detailMarker} is optional. When it is {@code null}, every typed row
 * that is not a subtotal or a total is treated as a detail row. When it is
 * supplied, a non-blank row type that matches none of the three markers is
 * reported as an error.</p>
 */
public final class ReconciliationConfig {

    private final int rowTypeColumn;
    private final int currencyColumn;
    private final int amountColumn;
    private final String detailMarker;
    private final String subtotalMarker;
    private final String totalMarker;

    /**
     * Convenience constructor without an explicit detail marker (any row that
     * is not a subtotal or total is a detail row).
     */
    public ReconciliationConfig(int rowTypeColumn, int currencyColumn, int amountColumn,
                                String subtotalMarker, String totalMarker) {
        this(rowTypeColumn, currencyColumn, amountColumn,
                null, subtotalMarker, totalMarker);
    }

    public ReconciliationConfig(int rowTypeColumn, int currencyColumn, int amountColumn,
                                String detailMarker, String subtotalMarker, String totalMarker) {
        if (rowTypeColumn < 0) {
            throw new IllegalArgumentException("rowTypeColumn must be zero or greater");
        }
        if (currencyColumn < 0) {
            throw new IllegalArgumentException("currencyColumn must be zero or greater");
        }
        if (amountColumn < 0) {
            throw new IllegalArgumentException("amountColumn must be zero or greater");
        }

        String subtotal = normalizeMarker(subtotalMarker);
        String total = normalizeMarker(totalMarker);
        String detail = normalizeMarker(detailMarker);

        if (subtotal == null) {
            throw new IllegalArgumentException("subtotalMarker is required");
        }
        if (total == null) {
            throw new IllegalArgumentException("totalMarker is required");
        }
        if (subtotal.equalsIgnoreCase(total)) {
            throw new IllegalArgumentException("subtotalMarker and totalMarker must differ");
        }
        if (detail != null && (detail.equalsIgnoreCase(subtotal) || detail.equalsIgnoreCase(total))) {
            throw new IllegalArgumentException("detailMarker must differ from subtotalMarker and totalMarker");
        }

        this.rowTypeColumn = rowTypeColumn;
        this.currencyColumn = currencyColumn;
        this.amountColumn = amountColumn;
        this.detailMarker = detail;
        this.subtotalMarker = subtotal;
        this.totalMarker = total;
    }

    private static String normalizeMarker(String marker) {
        if (marker == null) {
            return null;
        }
        String trimmed = marker.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public int getRowTypeColumn() {
        return rowTypeColumn;
    }

    public int getCurrencyColumn() {
        return currencyColumn;
    }

    public int getAmountColumn() {
        return amountColumn;
    }

    /**
     * @return the detail marker, or {@code null} if any non-subtotal/non-total
     *         row should be treated as a detail row.
     */
    public String getDetailMarker() {
        return detailMarker;
    }

    public String getSubtotalMarker() {
        return subtotalMarker;
    }

    public String getTotalMarker() {
        return totalMarker;
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Parses a compact specification of the form
     * {@code rowTypeColumn,currencyColumn,amountColumn,detailMarker,subtotalMarker,totalMarker}.
     * Column indexes are zero-based. Markers may not contain a comma. Leave the
     * detail marker field empty to treat any non-subtotal/non-total row as a
     * detail row.
     */
    public static ReconciliationConfig fromSpec(String spec) {
        if (spec == null) {
            throw new IllegalArgumentException("spec is required");
        }
        String[] fields = spec.split(",", -1);
        if (fields.length != 6) {
            throw new IllegalArgumentException("expected 6 comma-separated fields but found " + fields.length);
        }
        int rowTypeColumn;
        int currencyColumn;
        int amountColumn;
        try {
            rowTypeColumn = Integer.parseInt(fields[0].trim());
            currencyColumn = Integer.parseInt(fields[1].trim());
            amountColumn = Integer.parseInt(fields[2].trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("column indexes must be integers");
        }
        String detail = fields[3].trim();
        return new ReconciliationConfig(
                rowTypeColumn,
                currencyColumn,
                amountColumn,
                detail.isEmpty() ? null : detail,
                fields[4].trim(),
                fields[5].trim());
    }

    @Override
    public String toString() {
        return String.format(Locale.ROOT,
                "ReconciliationConfig{rowTypeColumn=%d, currencyColumn=%d, amountColumn=%d, "
                        + "detailMarker=%s, subtotalMarker=%s, totalMarker=%s}",
                rowTypeColumn, currencyColumn, amountColumn,
                detailMarker, subtotalMarker, totalMarker);
    }

    public static final class Builder {
        private int rowTypeColumn = -1;
        private int currencyColumn = -1;
        private int amountColumn = -1;
        private String detailMarker;
        private String subtotalMarker;
        private String totalMarker;

        private Builder() {
        }

        public Builder rowTypeColumn(int column) {
            this.rowTypeColumn = column;
            return this;
        }

        public Builder currencyColumn(int column) {
            this.currencyColumn = column;
            return this;
        }

        public Builder amountColumn(int column) {
            this.amountColumn = column;
            return this;
        }

        public Builder detailMarker(String marker) {
            this.detailMarker = marker;
            return this;
        }

        public Builder subtotalMarker(String marker) {
            this.subtotalMarker = marker;
            return this;
        }

        public Builder totalMarker(String marker) {
            this.totalMarker = marker;
            return this;
        }

        // JavaBeans-style aliases, for callers who prefer them.
        public Builder setRowTypeColumn(int column) {
            return rowTypeColumn(column);
        }

        public Builder setCurrencyColumn(int column) {
            return currencyColumn(column);
        }

        public Builder setAmountColumn(int column) {
            return amountColumn(column);
        }

        public Builder setDetailMarker(String marker) {
            return detailMarker(marker);
        }

        public Builder setSubtotalMarker(String marker) {
            return subtotalMarker(marker);
        }

        public Builder setTotalMarker(String marker) {
            return totalMarker(marker);
        }

        public ReconciliationConfig build() {
            if (rowTypeColumn < 0) {
                throw new IllegalStateException("rowTypeColumn is required");
            }
            if (currencyColumn < 0) {
                throw new IllegalStateException("currencyColumn is required");
            }
            if (amountColumn < 0) {
                throw new IllegalStateException("amountColumn is required");
            }
            return new ReconciliationConfig(rowTypeColumn, currencyColumn, amountColumn,
                    detailMarker, subtotalMarker, totalMarker);
        }
    }
}
