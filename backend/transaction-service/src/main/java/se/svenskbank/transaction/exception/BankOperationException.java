package se.svenskbank.transaction.exception;

/** A business error, e.g. account not found or insufficient funds (HTTP 400). */
public class BankOperationException extends RuntimeException {

    public BankOperationException(String message) {
        super(message);
    }
}
