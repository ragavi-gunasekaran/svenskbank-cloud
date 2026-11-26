package se.svenskbank.transaction.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import se.svenskbank.transaction.model.Transaction;

import java.util.List;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    List<Transaction> findByFromAccountIdOrToAccountIdOrderByCreatedAtDesc(Long fromAccountId, Long toAccountId);
}
