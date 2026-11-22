package technology.tabula.reconciliation;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import technology.tabula.RectangularTextContainer;
import technology.tabula.Table;

/**
 * Reconciles extracted table rows per table and per currency.
 *
 * <p>Every {@link Table} is accounted independently. Each row is classified by
 * the configured row-type column as a detail row, a subtotal row, or a total
 * row. Currencies may be interleaved: the accounting state is kept per currency
 * key inside the table.</p>
 *
 * <ul>
 *   <li>A <strong>subtotal</strong> is compared with the details for its
 *       currency accumulated since the previous subtotal of that currency, then
 *       that accumulated segment is cleared.</li>
 *   <li>A <strong>total</strong> is compared with every detail of its currency
 *       in the whole table.</li>
 *   <li>Every currency that appears on the table must have exactly one closing
 *       total, and every detail must have been closed by a subtotal by the end
 *       of the table.</li>
 * </ul>
 *
 * <p>Amounts are summed as exact {@link BigDecimal} decimal fixed-point values,
 * never as floating point, so cent-level equality is meaningful.</p>
 *
 * <p>When any error is found no detail rows are produced at all: partial data
 * from a statement that failed reconciliation must not be ingested.</p>
 */
public final class Reconciler {

    private enum RowKind {
        DETAIL, SUBTOTAL, TOTAL
    }

    private static final class CurrencyState {
        private String displayCurrency;
        private BigDecimal pending = BigDecimal.ZERO;
        private int pendingCount = 0;
        private int pendingLastRow = -1;
        private BigDecimal all = BigDecimal.ZERO;
        private int lastRow = -1;
        private boolean closed = false;
    }

    private final ReconciliationConfig config;
    private final String normalizedDetailMarker;
    private final String normalizedSubtotalMarker;
    private final String normalizedTotalMarker;

    public Reconciler(ReconciliationConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("config is required");
        }
        this.config = config;
        this.normalizedDetailMarker = normalizeMarker(config.getDetailMarker());
        this.normalizedSubtotalMarker = normalizeMarker(config.getSubtotalMarker());
        this.normalizedTotalMarker = normalizeMarker(config.getTotalMarker());
    }

    public ReconciliationConfig getConfig() {
        return config;
    }

    public ReconciliationResult reconcile(List<Table> tables) {
        List<ReconciledRow> accepted = new ArrayList<>();
        List<ReconciliationError> errors = new ArrayList<>();

        if (tables != null) {
            for (Table table : tables) {
                if (table == null) {
                    continue;
                }
                reconcileTable(table, accepted, errors);
            }
        }

        if (!errors.isEmpty()) {
            // Do not emit details for a run that failed reconciliation.
            return new ReconciliationResult(Collections.<ReconciledRow>emptyList(), errors);
        }
        return new ReconciliationResult(accepted, errors);
    }

    public ReconciliationResult reconcile(Table table) {
        return reconcile(Collections.singletonList(table));
    }

    private void reconcileTable(Table table, List<ReconciledRow> accepted,
                                List<ReconciliationError> errors) {
        List<List<RectangularTextContainer>> rows = table.getRows();
        if (rows.isEmpty()) {
            return;
        }

        int pageNumber = table.getPageNumber();
        Map<String, CurrencyState> states = new LinkedHashMap<>();
        List<ReconciledRow> tableAccepted = new ArrayList<>();

        for (int r = 0; r < rows.size(); r++) {
            List<RectangularTextContainer> cells = rows.get(r);
            int rowNumber = r + 1;

            if (isBlankRow(cells)) {
                continue;
            }

            String typeText = cellText(cells, config.getRowTypeColumn());
            RowKind kind = classify(typeText);
            if (kind == null) {
                // A row whose type matches none of the configured markers is not
                // an accounted row (header, note, ...).
                continue;
            }

            String currencyRaw = cellText(cells, config.getCurrencyColumn());
            if (currencyRaw.trim().isEmpty()) {
                errors.add(new ReconciliationError(ReconciliationError.Kind.INVALID_CURRENCY,
                        pageNumber, rowNumber, "", "row type " + typeText.trim()));
                continue;
            }

            String currencyKey = AmountParser.currencyKey(currencyRaw);
            CurrencyState state = states.get(currencyKey);
            if (state == null) {
                state = new CurrencyState();
                states.put(currencyKey, state);
            }
            state.displayCurrency = currencyRaw.trim();
            state.lastRow = rowNumber;

            if (state.closed) {
                errors.add(new ReconciliationError(ReconciliationError.Kind.CURRENCY_AFTER_TOTAL,
                        pageNumber, rowNumber, state.displayCurrency,
                        "row type " + typeText.trim()));
                continue;
            }

            BigDecimal amount;
            try {
                amount = AmountParser.parse(cellText(cells, config.getAmountColumn()));
            } catch (InvalidAmountException e) {
                errors.add(new ReconciliationError(ReconciliationError.Kind.INVALID_AMOUNT,
                        pageNumber, rowNumber, state.displayCurrency, e.getMessage()));
                if (kind == RowKind.SUBTOTAL) {
                    // Treat the printed subtotal as the segment boundary even
                    // though its value could not be verified.
                    state.pending = BigDecimal.ZERO;
                    state.pendingCount = 0;
                    state.pendingLastRow = -1;
                } else if (kind == RowKind.TOTAL) {
                    // The printed total closes the currency; avoid also
                    // reporting a missing total for it.
                    state.closed = true;
                    state.pending = BigDecimal.ZERO;
                    state.pendingCount = 0;
                    state.pendingLastRow = -1;
                }
                continue;
            }

            switch (kind) {
                case DETAIL:
                    state.pending = state.pending.add(amount);
                    state.pendingCount++;
                    state.pendingLastRow = rowNumber;
                    state.all = state.all.add(amount);
                    tableAccepted.add(new ReconciledRow(pageNumber, rowNumber, cells,
                            state.displayCurrency, amount));
                    break;

                case SUBTOTAL:
                    if (amount.compareTo(state.pending) != 0) {
                        errors.add(new ReconciliationError(
                                ReconciliationError.Kind.SUBTOTAL_MISMATCH,
                                pageNumber, rowNumber, state.displayCurrency,
                                "details total " + state.pending.toPlainString()
                                        + " but subtotal is " + amount.toPlainString()));
                    }
                    state.pending = BigDecimal.ZERO;
                    state.pendingCount = 0;
                    state.pendingLastRow = -1;
                    break;

                case TOTAL:
                    if (state.pendingCount > 0) {
                        errors.add(new ReconciliationError(
                                ReconciliationError.Kind.UNCLOSED_SEGMENT,
                                pageNumber, rowNumber, state.displayCurrency,
                                state.pendingCount + " detail row(s) were not closed by a subtotal"));
                    }
                    if (amount.compareTo(state.all) != 0) {
                        errors.add(new ReconciliationError(
                                ReconciliationError.Kind.TOTAL_MISMATCH,
                                pageNumber, rowNumber, state.displayCurrency,
                                "details total " + state.all.toPlainString()
                                        + " but total is " + amount.toPlainString()));
                    }
                    state.closed = true;
                    state.pending = BigDecimal.ZERO;
                    state.pendingCount = 0;
                    state.pendingLastRow = -1;
                    break;

                default:
                    break;
            }
        }

        // End of table: every currency must be closed by exactly one total and
        // every detail must have passed through a subtotal.
        for (CurrencyState state : states.values()) {
            if (!state.closed) {
                if (state.pendingCount > 0) {
                    errors.add(new ReconciliationError(
                            ReconciliationError.Kind.UNCLOSED_SEGMENT,
                            pageNumber,
                            state.pendingLastRow >= 0 ? state.pendingLastRow : state.lastRow,
                            state.displayCurrency,
                            state.pendingCount + " detail row(s) were not closed by a subtotal"));
                }
                errors.add(new ReconciliationError(
                        ReconciliationError.Kind.MISSING_TOTAL,
                        pageNumber, state.lastRow, state.displayCurrency,
                        "no closing total was found"));
            }
        }

        accepted.addAll(tableAccepted);
    }

    private RowKind classify(String typeText) {
        String normalized = normalizeMarker(typeText);
        if (normalized == null) {
            return null;
        }
        if (normalized.equals(normalizedSubtotalMarker)) {
            return RowKind.SUBTOTAL;
        }
        if (normalized.equals(normalizedTotalMarker)) {
            return RowKind.TOTAL;
        }
        if (normalizedDetailMarker != null) {
            return normalized.equals(normalizedDetailMarker) ? RowKind.DETAIL : null;
        }
        return RowKind.DETAIL;
    }

    private static String normalizeMarker(String marker) {
        if (marker == null) {
            return null;
        }
        String trimmed = marker.trim().replaceAll("\\s+", " ");
        return trimmed.isEmpty() ? null : trimmed.toUpperCase(java.util.Locale.ROOT);
    }

    private static String cellText(List<RectangularTextContainer> cells, int column) {
        if (cells == null || column < 0 || column >= cells.size()) {
            return "";
        }
        RectangularTextContainer cell = cells.get(column);
        if (cell == null) {
            return "";
        }
        String text = cell.getText();
        return text == null ? "" : text;
    }

    private static boolean isBlankRow(List<RectangularTextContainer> cells) {
        if (cells == null) {
            return true;
        }
        for (RectangularTextContainer cell : cells) {
            if (cell != null) {
                String text = cell.getText();
                if (text != null && !text.trim().isEmpty()) {
                    return false;
                }
            }
        }
        return true;
    }
}
