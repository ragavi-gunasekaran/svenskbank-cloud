package se.svenskbank.account.exception;

public class InsufficientFundsException extends RuntimeException {

    public InsufficientFundsException(Long id) {
        super("Insufficient funds on account " + id);
    }
}
