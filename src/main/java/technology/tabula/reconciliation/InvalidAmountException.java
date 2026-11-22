package technology.tabula.reconciliation;

/**
 * Thrown when a cell in the amount column is not a valid decimal amount.
 */
public class InvalidAmountException extends IllegalArgumentException {

    private static final long serialVersionUID = 1L;

    public InvalidAmountException(String message) {
        super(message);
    }

    public InvalidAmountException(String message, Throwable cause) {
        super(message, cause);
    }
}
