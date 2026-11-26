package se.svenskbank.transaction.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import se.svenskbank.transaction.client.AccountClient;
import se.svenskbank.transaction.dto.DepositRequest;
import se.svenskbank.transaction.dto.TransactionResponse;
import se.svenskbank.transaction.dto.TransferRequest;
import se.svenskbank.transaction.dto.WithdrawRequest;
import se.svenskbank.transaction.exception.BankOperationException;
import se.svenskbank.transaction.model.Transaction;
import se.svenskbank.transaction.model.TransactionType;
import se.svenskbank.transaction.repository.TransactionRepository;

import java.util.List;

@Service
public class TransactionService {

    private static final Logger log = LoggerFactory.getLogger(TransactionService.class);

    private final TransactionRepository transactionRepository;
    private final AccountClient accountClient;

    public TransactionService(TransactionRepository transactionRepository, AccountClient accountClient) {
        this.transactionRepository = transactionRepository;
        this.accountClient = accountClient;
    }

    public TransactionResponse deposit(DepositRequest request) {
        log.info("Deposit of {} to account id={}", request.amount(), request.accountId());
        accountClient.updateBalance(request.accountId(), request.amount());
        return save(new Transaction(TransactionType.DEPOSIT, null, request.accountId(), request.amount()));
    }

    public TransactionResponse withdraw(WithdrawRequest request) {
        log.info("Withdrawal of {} from account id={}", request.amount(), request.accountId());
        accountClient.updateBalance(request.accountId(), request.amount().negate());
        return save(new Transaction(TransactionType.WITHDRAW, request.accountId(), null, request.amount()));
    }

    /**
     * Transfer = withdraw from one account + deposit to the other.
     * The two accounts are updated by separate REST calls, so there is no shared
     * database transaction. If the deposit fails, the withdrawal is undone
     * (a "compensating action", as in the Saga pattern).
     */
    public TransactionResponse transfer(TransferRequest request) {
        if (request.fromAccountId().equals(request.toAccountId())) {
            throw new BankOperationException("Cannot transfer to the same account");
        }
        log.info("Transfer of {} from account id={} to account id={}",
                request.amount(), request.fromAccountId(), request.toAccountId());

        accountClient.updateBalance(request.fromAccountId(), request.amount().negate());
        try {
            accountClient.updateBalance(request.toAccountId(), request.amount());
        } catch (RuntimeException ex) {
            log.warn("Transfer deposit failed, refunding account {}", request.fromAccountId());
            accountClient.updateBalance(request.fromAccountId(), request.amount());
            throw ex;
        }

        return save(new Transaction(
                TransactionType.TRANSFER, request.fromAccountId(), request.toAccountId(), request.amount()));
    }

    public List<TransactionResponse> history(Long accountId) {
        return transactionRepository
                .findByFromAccountIdOrToAccountIdOrderByCreatedAtDesc(accountId, accountId).stream()
                .map(TransactionResponse::from)
                .toList();
    }

    private TransactionResponse save(Transaction transaction) {
        Transaction saved = transactionRepository.save(transaction);
        log.info("Saved {} transaction id={}", saved.getType(), saved.getId());
        return TransactionResponse.from(saved);
    }
}
