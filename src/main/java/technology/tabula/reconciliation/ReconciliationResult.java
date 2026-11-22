package technology.tabula.reconciliation;

import java.util.Collections;
import java.util.List;

/**
 * The outcome of reconciling a list of tables: the detail rows to emit (in
 * original order) and any errors found.
 *
 * <p>{@link #isSuccess()} is false whenever at least one error was found. In
 * that case {@link #getRows()} is empty, by design: a failed reconciliation must
 * not contribute partial detail rows to the fact table.</p>
 */
public final class ReconciliationResult {

    public static final ReconciliationResult EMPTY =
            new ReconciliationResult(Collections.<ReconciledRow>emptyList(),
                    Collections.<ReconciliationError>emptyList());

    private final List<ReconciledRow> rows;
    private final List<ReconciliationError> errors;

    public ReconciliationResult(List<ReconciledRow> rows, List<ReconciliationError> errors) {
        this.rows = Collections.unmodifiableList(rows);
        this.errors = Collections.unmodifiableList(errors);
    }

    public boolean isSuccess() {
        return errors.isEmpty();
    }

    public List<ReconciledRow> getRows() {
        return rows;
    }

    public List<ReconciliationError> getErrors() {
        return errors;
    }
}
