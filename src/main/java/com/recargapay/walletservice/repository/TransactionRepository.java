package com.recargapay.walletservice.repository;

import com.recargapay.walletservice.entity.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, UUID> {
    
    @Query("SELECT t FROM Transaction t WHERE t.wallet.id = :walletId AND DATE(t.createdAt) = :date ORDER BY t.createdAt DESC")
    List<Transaction> findTransactionsOnDate(@Param("walletId") UUID walletId, @Param("date") LocalDate date);
    
    default Optional<Transaction> findLastTransactionOnDate(UUID walletId, LocalDate date) {
        List<Transaction> transactions = findTransactionsOnDate(walletId, date);
        return transactions.isEmpty() ? Optional.empty() : Optional.of(transactions.get(0));
    }
    
    List<Transaction> findByWalletIdOrderByCreatedAtDesc(UUID walletId);
}
