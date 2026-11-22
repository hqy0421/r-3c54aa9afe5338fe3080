package technology.tabula.reconciliation;

import java.util.Collections;
import java.util.List;

/**
 * Thrown by the CSV writer when reconciliation mode is enabled and one or more
 * rows fail reconciliation. The message is a human-readable report listing the
 * page, row, and currency of each problem.
 *
 * <p>When this is thrown nothing has been written: partially reconciled detail
 * rows are never emitted.</p>
 */
public class ReconciliationException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final List<ReconciliationError> errors;

    public ReconciliationException(List<ReconciliationError> errors) {
        super(buildMessage(errors));
        this.errors = Collections.unmodifiableList(errors);
    }

    public List<ReconciliationError> getErrors() {
        return errors;
    }

    private static String buildMessage(List<ReconciliationError> errors) {
        StringBuilder sb = new StringBuilder("reconciliation failed:");
        for (ReconciliationError error : errors) {
            sb.append(System.lineSeparator()).append(error.toString());
        }
        return sb.toString();
    }
}
