package com.recargapay.walletservice.repository;

import com.recargapay.walletservice.entity.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, UUID> {
    
    @Query("SELECT t FROM Transaction t WHERE t.wallet.id = :walletId AND t.createdAt <= :timestamp ORDER BY t.createdAt DESC LIMIT 1")
    Optional<Transaction> findLastTransactionBeforeOrAt(@Param("walletId") UUID walletId, @Param("timestamp") LocalDateTime timestamp);
    
    List<Transaction> findByWalletIdOrderByCreatedAtDesc(UUID walletId);
}
