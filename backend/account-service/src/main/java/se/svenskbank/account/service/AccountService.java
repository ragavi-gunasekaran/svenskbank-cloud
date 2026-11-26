package se.svenskbank.account.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import se.svenskbank.account.dto.AccountResponse;
import se.svenskbank.account.dto.CreateAccountRequest;
import se.svenskbank.account.exception.AccountNotFoundException;
import se.svenskbank.account.exception.InsufficientFundsException;
import se.svenskbank.account.model.Account;
import se.svenskbank.account.repository.AccountRepository;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class AccountService {

    private static final Logger log = LoggerFactory.getLogger(AccountService.class);

    private final AccountRepository accountRepository;

    public AccountService(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    @Transactional(readOnly = true)
    public List<AccountResponse> findAll() {
        return accountRepository.findAll().stream()
                .map(AccountResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public AccountResponse findById(Long id) {
        return accountRepository.findById(id)
                .map(AccountResponse::from)
                .orElseThrow(() -> new AccountNotFoundException(id));
    }

    @Transactional
    public AccountResponse create(CreateAccountRequest request) {
        BigDecimal initial = request.initialDeposit() == null ? BigDecimal.ZERO : request.initialDeposit();
        Account account = accountRepository.save(new Account(generateAccountNumber(), request.ownerName().trim(), initial));
        log.info("Created account id={} number={} initialBalance={}", account.getId(), account.getAccountNumber(), initial);
        return AccountResponse.from(account);
    }

    /**
     * Adds (positive) or removes (negative) money. The balance can never go below zero.
     */
    @Transactional
    public AccountResponse updateBalance(Long id, BigDecimal amount) {
        Account account = accountRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new AccountNotFoundException(id));

        BigDecimal newBalance = account.getBalance().add(amount);
        if (newBalance.signum() < 0) {
            log.warn("Rejected balance change of {} on account id={}: insufficient funds", amount, id);
            throw new InsufficientFundsException(id);
        }
        account.setBalance(newBalance);
        log.info("Balance of account id={} changed by {} -> new balance {}", id, amount, newBalance);
        return AccountResponse.from(account);
    }

    /** Account numbers look like "SB" + 10 digits, e.g. SB4821937650. */
    private String generateAccountNumber() {
        String number;
        do {
            long digits = ThreadLocalRandom.current().nextLong(1_000_000_000L, 10_000_000_000L);
            number = "SB" + digits;
        } while (accountRepository.existsByAccountNumber(number));
        return number;
    }
}
