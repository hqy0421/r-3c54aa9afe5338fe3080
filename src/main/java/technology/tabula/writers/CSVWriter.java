package technology.tabula.writers;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVFormat;

import technology.tabula.RectangularTextContainer;
import technology.tabula.Table;
import technology.tabula.reconciliation.ReconciledRow;
import technology.tabula.reconciliation.ReconciliationConfig;
import technology.tabula.reconciliation.ReconciliationException;
import technology.tabula.reconciliation.ReconciliationResult;
import technology.tabula.reconciliation.Reconciler;

public class CSVWriter implements Writer {

	private final CSVFormat format;
	private final ReconciliationConfig reconciliationConfig;

    public CSVWriter() {
        this(CSVFormat.EXCEL);
    }

    protected CSVWriter(CSVFormat format) {
        this(format, null);
    }

    protected CSVWriter(CSVFormat format, ReconciliationConfig reconciliationConfig) {
        this.format = format;
        this.reconciliationConfig = reconciliationConfig;
    }

    /**
     * Creates a CSV writer in reconciliation mode. When reconciliation succeeds
     * only the detail rows are written, in their original order; subtotal and
     * total rows are never written. When it fails, no rows are written and a
     * {@link ReconciliationException} is thrown describing each problem.
     */
    public static CSVWriter reconciling(ReconciliationConfig config) {
        return new CSVWriter(CSVFormat.EXCEL, config);
    }

    /**
     * @return the reconciliation configuration, or {@code null} for plain mode
     */
    public ReconciliationConfig getReconciliationConfig() {
        return this.reconciliationConfig;
    }

	// - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - //
    @Override
    public void write(Appendable out, Table table) throws IOException {
        write(out, Collections.singletonList(table));
    }

    @Override
    public void write(Appendable out, List<Table> tables) throws IOException {
        if (this.reconciliationConfig != null) {
            writeReconciled(out, tables);
            return;
        }

        try (CSVPrinter printer = new CSVPrinter(out, format)) {
            for (Table table : tables) {
                for (List<RectangularTextContainer> row : table.getRows()) {
                    List<String> cells = new ArrayList<>(row.size());
                    for (RectangularTextContainer<?> cell : row)
                    	cells.add(cell.getText());
                    printer.printRecord(cells);
                }
            }
            printer.flush();
        }
    }

    private void writeReconciled(Appendable out, List<Table> tables) throws IOException {
        ReconciliationResult result = new Reconciler(this.reconciliationConfig)
                .reconcile(new ArrayList<>(tables));
        if (!result.isSuccess()) {
            throw new ReconciliationException(result.getErrors());
        }

        try (CSVPrinter printer = new CSVPrinter(out, format)) {
            for (ReconciledRow row : result.getRows()) {
                List<RectangularTextContainer> cells = row.getCells();
                List<String> texts = new ArrayList<>(cells.size());
                for (RectangularTextContainer<?> cell : cells)
                    texts.add(cell.getText());
                printer.printRecord(texts);
            }
            printer.flush();
        }
    }

}
