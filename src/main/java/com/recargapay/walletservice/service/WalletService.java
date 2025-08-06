package com.recargapay.walletservice.service;

import com.recargapay.walletservice.dto.BalanceResponse;
import com.recargapay.walletservice.entity.Transaction;
import com.recargapay.walletservice.entity.TransactionType;
import com.recargapay.walletservice.entity.Wallet;
import com.recargapay.walletservice.exception.InsufficientFundsException;
import com.recargapay.walletservice.exception.WalletNotFoundException;
import com.recargapay.walletservice.repository.TransactionRepository;
import com.recargapay.walletservice.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
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
        BigDecimal currentBalance = formatBigDecimal(wallet.getBalance());
        log.info("Current balance for wallet {}: {}", walletId, currentBalance);
        
        BalanceResponse response = new BalanceResponse();
        response.setWalletId(wallet.getId());
        response.setBalance(currentBalance);
        response.setBalanceAfter(currentBalance);
        return response;
    }

    @Transactional(readOnly = true)
    public BigDecimal getHistoricalBalance(UUID walletId, LocalDateTime timestamp) {
        log.info("Getting historical balance for wallet: {} at timestamp: {}", walletId, timestamp);
        
        // Find the last transaction before or at the given timestamp
        var lastTransaction = transactionRepository.findLastTransactionBeforeOrAt(walletId, timestamp);
        
        if (lastTransaction.isPresent()) {
            BigDecimal balance = formatBigDecimal(lastTransaction.get().getBalanceAfter());
            log.info("Historical balance for wallet {} at {}: {}", walletId, timestamp, balance);
            return balance;
        } else {
            // If no transaction found, calculate balance from all transactions up to timestamp
            BigDecimal calculatedBalance = formatBigDecimal(transactionRepository.sumTransactionsUpTo(walletId, timestamp));
            log.info("No transaction found, calculated balance: {}", calculatedBalance);
            return calculatedBalance;
        }
    }

    @Transactional
    public BalanceResponse deposit(UUID walletId, BigDecimal amount) {
        log.info("Processing deposit of {} for wallet: {}", amount, walletId);
        
        return executeWithRetry(() -> {
            Wallet wallet = findWalletById(walletId);
            BigDecimal newBalance = formatBigDecimal(wallet.getBalance().add(amount));
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
            
            BigDecimal newBalance = formatBigDecimal(wallet.getBalance().subtract(amount));
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
            BigDecimal sourceNewBalance = formatBigDecimal(sourceWallet.getBalance().subtract(amount));
            sourceWallet.setBalance(sourceNewBalance);
            walletRepository.save(sourceWallet);
            
            // Update target wallet
            BigDecimal targetNewBalance = formatBigDecimal(targetWallet.getBalance().add(amount));
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

    private BigDecimal formatBigDecimal(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private <T> T executeWithRetry(java.util.function.Supplier<T> operation) {
        int maxRetries = 3;
        int retryCount = 0;
        
        while (retryCount < maxRetries) {
            try {
                return operation.get();
            } catch (OptimisticLockingFailureException e) {
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
