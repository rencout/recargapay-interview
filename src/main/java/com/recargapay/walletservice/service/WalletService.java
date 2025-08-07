package com.recargapay.walletservice.service;

import com.recargapay.walletservice.dto.BalanceResponse;
import com.recargapay.walletservice.entity.Transaction;
import com.recargapay.walletservice.entity.TransactionType;
import com.recargapay.walletservice.entity.Wallet;
import com.recargapay.walletservice.exception.InsufficientFundsException;
import com.recargapay.walletservice.exception.WalletNotFoundException;
import com.recargapay.walletservice.exception.InvalidTimestampException;
import com.recargapay.walletservice.mapper.WalletMapper;
import com.recargapay.walletservice.repository.TransactionRepository;
import com.recargapay.walletservice.repository.WalletRepository;
import com.recargapay.walletservice.util.MoneyUtils;
import com.recargapay.walletservice.util.WalletConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.OptimisticLockingFailureException;
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
    private final WalletMapper walletMapper;

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
        log.info("Current balance for wallet {}: {}", walletId, wallet.getBalance());
        
        return walletMapper.toBalanceResponse(wallet);
    }

    @Transactional(readOnly = true)
    public BigDecimal getHistoricalBalance(UUID walletId, LocalDateTime timestamp) {
        log.info("Getting historical balance for wallet: {} at timestamp: {}", walletId, timestamp);
        
        // Validate timestamp is not in the future
        if (timestamp.isAfter(LocalDateTime.now())) {
            throw new InvalidTimestampException("Cannot get historical balance for future timestamp: " + timestamp);
        }
        
        // Find the last transaction before or at the given timestamp
        var lastTransaction = transactionRepository.findLastTransactionBeforeOrAt(walletId, timestamp);
        
        if (lastTransaction.isPresent()) {
            // Use the balance after the last transaction
            BigDecimal balance = MoneyUtils.format(lastTransaction.get().getBalanceAfter());
            log.info("Historical balance for wallet {} at {}: {} (from transaction)", walletId, timestamp, balance);
            return balance;
        } else {
            // No transactions found - check if wallet exists and return its initial balance
            Wallet wallet = findWalletById(walletId);
            
            // If wallet was created after the timestamp, return zero
            if (wallet.getCreatedAt().isAfter(timestamp)) {
                log.info("Wallet {} was created after timestamp {}, returning zero balance", walletId, timestamp);
                return MoneyUtils.zero();
            }
            
            // Calculate balance from all transactions up to timestamp
            BigDecimal calculatedBalance = MoneyUtils.format(transactionRepository.sumTransactionsUpTo(walletId, timestamp));
            log.info("No transaction found for wallet {} at {}, calculated balance: {}", walletId, timestamp, calculatedBalance);
            return calculatedBalance;
        }
    }

    @Transactional
    public BalanceResponse deposit(UUID walletId, BigDecimal amount) {
        log.info("Processing deposit of {} for wallet: {}", amount, walletId);
        return processTransaction(walletId, amount, TransactionType.DEPOSIT, false);
    }

    @Transactional
    public BalanceResponse withdraw(UUID walletId, BigDecimal amount) {
        log.info("Processing withdrawal of {} for wallet: {}", amount, walletId);
        return processTransaction(walletId, amount, TransactionType.WITHDRAW, true);
    }

    @Transactional
    public void transfer(UUID sourceWalletId, UUID targetWalletId, BigDecimal amount) {
        log.info("Processing transfer of {} from wallet {} to wallet {}", amount, sourceWalletId, targetWalletId);
        
        if (sourceWalletId.equals(targetWalletId)) {
            throw new IllegalArgumentException(WalletConstants.SAME_WALLET_ERROR);
        }
        
        if (amount.compareTo(WalletConstants.MIN_TRANSACTION_AMOUNT) < 0) {
            throw new IllegalArgumentException("Transfer amount must be at least " + WalletConstants.MIN_TRANSACTION_AMOUNT);
        }
        
        executeWithRetry(() -> {
            // Load both wallets with pessimistic locking to prevent concurrent modifications
            Wallet sourceWallet = walletRepository.findByIdWithLock(sourceWalletId)
                .orElseThrow(() -> new WalletNotFoundException("Source wallet not found with ID: " + sourceWalletId));
            Wallet targetWallet = walletRepository.findByIdWithLock(targetWalletId)
                .orElseThrow(() -> new WalletNotFoundException("Target wallet not found with ID: " + targetWalletId));
            
            // Validate source wallet has sufficient funds
            if (sourceWallet.getBalance().compareTo(amount) < 0) {
                throw new InsufficientFundsException(
                    String.format(WalletConstants.INSUFFICIENT_FUNDS_SOURCE_FORMAT, 
                        sourceWallet.getBalance(), amount)
                );
            }
            
            // Calculate new balances
            BigDecimal sourceNewBalance = MoneyUtils.subtract(sourceWallet.getBalance(), amount);
            BigDecimal targetNewBalance = MoneyUtils.add(targetWallet.getBalance(), amount);
            
            // Update both wallets atomically
            sourceWallet.setBalance(sourceNewBalance);
            targetWallet.setBalance(targetNewBalance);
            
            // Save both wallets
            Wallet savedSourceWallet = walletRepository.save(sourceWallet);
            Wallet savedTargetWallet = walletRepository.save(targetWallet);
            
            // Create transaction records for both wallets
            Transaction sourceTransaction = new Transaction(
                savedSourceWallet, 
                TransactionType.TRANSFER_OUT, 
                amount, 
                sourceNewBalance, 
                targetWalletId
            );
            
            Transaction targetTransaction = new Transaction(
                savedTargetWallet, 
                TransactionType.TRANSFER_IN, 
                amount, 
                targetNewBalance, 
                sourceWalletId
            );
            
            // Save both transactions
            transactionRepository.save(sourceTransaction);
            transactionRepository.save(targetTransaction);
            
            log.info("Transfer completed successfully. Source balance: {}, Target balance: {}", 
                    sourceNewBalance, targetNewBalance);
            
            return null;
        });
    }

    private Wallet findWalletById(UUID walletId) {
        return walletRepository.findById(walletId)
            .orElseThrow(() -> new WalletNotFoundException("Wallet not found with ID: " + walletId));
    }

    private <T> T executeWithRetry(java.util.function.Supplier<T> operation) {
        int maxRetries = WalletConstants.MAX_RETRIES;
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
                    Thread.sleep(WalletConstants.RETRY_DELAY_MS * retryCount); // Exponential backoff
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Thread interrupted during retry", ie);
                }
            }
        }
        
        throw new RuntimeException("Unexpected error during retry");
    }

    private BalanceResponse processTransaction(UUID walletId, BigDecimal amount, 
                                             TransactionType type, boolean isDebit) {
        return executeWithRetry(() -> {
            Wallet wallet = findWalletById(walletId);
            
            if (isDebit && wallet.getBalance().compareTo(amount) < 0) {
                throw new InsufficientFundsException(
                    String.format(WalletConstants.INSUFFICIENT_FUNDS_FORMAT, 
                        wallet.getBalance(), amount)
                );
            }
            
            BigDecimal newBalance = isDebit 
                ? MoneyUtils.subtract(wallet.getBalance(), amount)
                : MoneyUtils.add(wallet.getBalance(), amount);
            
            wallet.setBalance(newBalance);
            Wallet savedWallet = walletRepository.save(wallet);
            
            // Create transaction record
            Transaction transaction = new Transaction(wallet, type, amount, newBalance);
            transactionRepository.save(transaction);
            
            log.info("{} completed. New balance: {}", type, newBalance);
            
            return walletMapper.toBalanceResponse(savedWallet.getId(), savedWallet.getBalance(), newBalance);
        });
    }
}
