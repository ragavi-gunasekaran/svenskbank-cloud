package se.svenskbank.transaction.dto;

import se.svenskbank.transaction.model.Transaction;
import se.svenskbank.transaction.model.TransactionType;

import java.math.BigDecimal;
import java.time.Instant;

public record TransactionResponse(
        Long id,
        TransactionType type,
        Long fromAccountId,
        Long toAccountId,
        BigDecimal amount,
        Instant createdAt
) {
    public static TransactionResponse from(Transaction t) {
        return new TransactionResponse(
                t.getId(), t.getType(), t.getFromAccountId(), t.getToAccountId(), t.getAmount(), t.getCreatedAt());
    }
}
