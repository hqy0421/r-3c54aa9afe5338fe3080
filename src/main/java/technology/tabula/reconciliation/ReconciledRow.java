package technology.tabula.reconciliation;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

import technology.tabula.RectangularTextContainer;

/**
 * A detail row accepted by reconciliation, kept together with the metadata that
 * explains where it came from.
 */
public final class ReconciledRow {

    private final int pageNumber;
    private final int rowNumber;
    private final List<RectangularTextContainer> cells;
    private final String currency;
    private final BigDecimal amount;

    public ReconciledRow(int pageNumber, int rowNumber,
                         List<RectangularTextContainer> cells,
                         String currency, BigDecimal amount) {
        this.pageNumber = pageNumber;
        this.rowNumber = rowNumber;
        this.cells = Collections.unmodifiableList(cells);
        this.currency = currency;
        this.amount = amount;
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

    public List<RectangularTextContainer> getCells() {
        return cells;
    }

    public String getCurrency() {
        return currency;
    }

    public BigDecimal getAmount() {
        return amount;
    }
}
