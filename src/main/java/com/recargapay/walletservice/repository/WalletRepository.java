package com.recargapay.walletservice.repository;

import com.recargapay.walletservice.entity.Wallet;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface WalletRepository extends JpaRepository<Wallet, UUID> {
    
    Optional<Wallet> findByUserId(String userId);
    
    /**
     * Find wallet by ID with pessimistic locking to prevent concurrent modifications
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT w FROM Wallet w WHERE w.id = :walletId")
    Optional<Wallet> findByIdWithLock(@Param("walletId") UUID walletId);
}
