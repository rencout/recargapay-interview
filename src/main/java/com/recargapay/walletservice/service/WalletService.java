package com.recargapay.walletservice.service;

import com.recargapay.walletservice.dto.BalanceResponse;
import com.recargapay.walletservice.entity.Transaction;
import com.recargapay.walletservice.entity.TransactionType;
import com.recargapay.walletservice.entity.Wallet;
import com.recargapay.walletservice.exception.FutureTimestampException;
import com.recargapay.walletservice.exception.InsufficientFundsException;
import com.recargapay.walletservice.exception.WalletNotFoundException;
import com.recargapay.walletservice.repository.TransactionRepository;
import com.recargapay.walletservice.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class WalletService {

    private final WalletRepository walletRepository;
    private final TransactionRepository transactionRepository;

    @Transactional
    public Wallet createWallet(String userId) {
        log.info("Creating wallet for user: {}", userId);
        Wallet wallet = new Wallet(userId);
        Wallet savedWallet = walletRepository.save(wallet);
        log.info("Wallet created with ID: {}", savedWallet.getId());
        return savedWallet;
    }

    @Transactional(readOnly = true)
    public BalanceResponse getCurrentBalance(UUID walletId) {
        log.info("Getting current balance for wallet: {}", walletId);
        Wallet wallet = findWalletById(walletId);
        
        BalanceResponse response = new BalanceResponse();
        response.setWalletId(wallet.getId());
        response.setBalance(wallet.getBalance());
        response.setBalanceAfter(wallet.getBalance());
        
        log.info("Current balance for wallet {}: {}", walletId, wallet.getBalance());
        return response;
    }

    @Transactional(readOnly = true)
    public BigDecimal getHistoricalBalance(UUID walletId, LocalDateTime timestamp) {
        log.info("Getting historical balance for wallet: {} at timestamp: {}", walletId, timestamp);
        
        // Validate timestamp is not in the future
        if (timestamp.isAfter(LocalDateTime.now())) {
            throw new FutureTimestampException("Cannot retrieve historical balance for future timestamp: " + timestamp);
        }
        
        // Validate wallet exists
        findWalletById(walletId);
        
        // Find the last transaction before or at the timestamp
        var lastTransaction = transactionRepository.findLastTransactionBeforeOrAt(walletId, timestamp);
        
        if (lastTransaction.isPresent()) {
            log.info("Found last transaction, returning balanceAfter: {}", lastTransaction.get().getBalanceAfter());
            return lastTransaction.get().getBalanceAfter();
        } else {
            // If no transaction found, return 0 as per requirements
            log.info("No transaction found before or at timestamp, returning balance: 0");
            return BigDecimal.ZERO;
        }
    }

    @Transactional
    public BalanceResponse deposit(UUID walletId, BigDecimal amount) {
        log.info("Processing deposit of {} for wallet: {}", amount, walletId);
        
        return executeWithRetry(() -> {
            Wallet wallet = findWalletById(walletId);
            BigDecimal newBalance = wallet.getBalance().add(amount);
            
            wallet.setBalance(newBalance);
            Wallet savedWallet = walletRepository.save(wallet);
            
            // Create transaction record
            Transaction transaction = new Transaction(wallet, TransactionType.DEPOSIT, amount, newBalance);
            transactionRepository.save(transaction);
            
            log.info("Deposit completed. New balance: {}", newBalance);
            
            BalanceResponse response = new BalanceResponse();
            response.setWalletId(savedWallet.getId());
            response.setBalance(savedWallet.getBalance());
            response.setBalanceAfter(newBalance);
            return response;
        });
    }

    @Transactional
    public BalanceResponse withdraw(UUID walletId, BigDecimal amount) {
        log.info("Processing withdrawal of {} for wallet: {}", amount, walletId);
        
        return executeWithRetry(() -> {
            Wallet wallet = findWalletById(walletId);
            
            if (wallet.getBalance().compareTo(amount) < 0) {
                throw new InsufficientFundsException(
                    String.format("Insufficient funds. Current balance: %s, requested amount: %s", 
                        wallet.getBalance(), amount)
                );
            }
            
            BigDecimal newBalance = wallet.getBalance().subtract(amount);
            wallet.setBalance(newBalance);
            Wallet savedWallet = walletRepository.save(wallet);
            
            // Create transaction record
            Transaction transaction = new Transaction(wallet, TransactionType.WITHDRAW, amount, newBalance);
            transactionRepository.save(transaction);
            
            log.info("Withdrawal completed. New balance: {}", newBalance);
            
            BalanceResponse response = new BalanceResponse();
            response.setWalletId(savedWallet.getId());
            response.setBalance(savedWallet.getBalance());
            response.setBalanceAfter(newBalance);
            return response;
        });
    }

    @Transactional
    public void transfer(UUID sourceWalletId, UUID targetWalletId, BigDecimal amount) {
        log.info("Processing transfer of {} from wallet {} to wallet {}", amount, sourceWalletId, targetWalletId);
        
        if (sourceWalletId.equals(targetWalletId)) {
            throw new IllegalArgumentException("Source and target wallets cannot be the same");
        }
        
        executeWithRetry(() -> {
            Wallet sourceWallet = findWalletById(sourceWalletId);
            Wallet targetWallet = findWalletById(targetWalletId);
            
            if (sourceWallet.getBalance().compareTo(amount) < 0) {
                throw new InsufficientFundsException(
                    String.format("Insufficient funds in source wallet. Current balance: %s, requested amount: %s", 
                        sourceWallet.getBalance(), amount)
                );
            }
            
            // Update source wallet
            BigDecimal sourceNewBalance = sourceWallet.getBalance().subtract(amount);
            sourceWallet.setBalance(sourceNewBalance);
            walletRepository.save(sourceWallet);
            
            // Update target wallet
            BigDecimal targetNewBalance = targetWallet.getBalance().add(amount);
            targetWallet.setBalance(targetNewBalance);
            walletRepository.save(targetWallet);
            
            // Create transaction records
            Transaction sourceTransaction = new Transaction(sourceWallet, TransactionType.TRANSFER_OUT, amount, sourceNewBalance, targetWalletId);
            Transaction targetTransaction = new Transaction(targetWallet, TransactionType.TRANSFER_IN, amount, targetNewBalance, sourceWalletId);
            
            transactionRepository.save(sourceTransaction);
            transactionRepository.save(targetTransaction);
            
            log.info("Transfer completed. Source balance: {}, Target balance: {}", sourceNewBalance, targetNewBalance);
            return null;
        });
    }

    private Wallet findWalletById(UUID walletId) {
        return walletRepository.findById(walletId)
            .orElseThrow(() -> new WalletNotFoundException("Wallet not found with ID: " + walletId));
    }

    private <T> T executeWithRetry(java.util.function.Supplier<T> operation) {
        int maxRetries = 3;
        int retryCount = 0;
        
        while (retryCount < maxRetries) {
            try {
                return operation.get();
            } catch (ObjectOptimisticLockingFailureException e) {
                retryCount++;
                if (retryCount >= maxRetries) {
                    log.error("Max retries reached for optimistic locking failure", e);
                    throw e;
                }
                log.warn("Optimistic locking failure, retrying... (attempt {}/{})", retryCount, maxRetries);
                try {
                    Thread.sleep(100 * retryCount); // Exponential backoff
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Thread interrupted during retry", ie);
                }
            }
        }
        
        throw new RuntimeException("Unexpected error during retry");
    }
}
