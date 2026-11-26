package se.svenskbank.transaction.exception;

/** account-service could not be reached or failed (HTTP 503). */
public class AccountServiceUnavailableException extends RuntimeException {

    public AccountServiceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
