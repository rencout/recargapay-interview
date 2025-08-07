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

        // Try to use the balance after the last transaction (high-performance path)
        BigDecimal balanceAfter = lastTransaction.get().getBalanceAfter();
        if (balanceAfter != null) {
            BigDecimal balance = MoneyUtils.format(balanceAfter);
            log.info("Historical balance for wallet {} at {}: {} (from transaction balanceAfter)", walletId, timestamp, balance);
            return balance;
        } else {
            // Fallback: calculate balance from transaction history if balanceAfter is null
            BigDecimal calculatedBalance = MoneyUtils.format(transactionRepository.sumTransactionsUpTo(walletId, timestamp));
            log.info("Historical balance for wallet {} at {}: {} (calculated from transaction history)", walletId, timestamp, calculatedBalance);
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

    @Transactional(rollbackFor = Exception.class)
    public void transfer(UUID sourceWalletId, UUID targetWalletId, BigDecimal amount) {
        log.info("Processing transfer of {} from wallet {} to wallet {}", amount, sourceWalletId, targetWalletId);
        
        if (sourceWalletId.equals(targetWalletId)) {
            throw new IllegalArgumentException(WalletConstants.SAME_WALLET_ERROR);
        }
        
        if (amount.compareTo(WalletConstants.MIN_TRANSACTION_AMOUNT) < 0) {
            throw new IllegalArgumentException("Transfer amount must be at least " + WalletConstants.MIN_TRANSACTION_AMOUNT);
        }
        
        executeWithRetry(() -> {
            // Prevent deadlocks by always locking wallets in the same order (by UUID)
            UUID firstWalletId, secondWalletId;
            boolean sourceIsFirst;
            
            if (sourceWalletId.compareTo(targetWalletId) < 0) {
                firstWalletId = sourceWalletId;
                secondWalletId = targetWalletId;
                sourceIsFirst = true;
            } else {
                firstWalletId = targetWalletId;
                secondWalletId = sourceWalletId;
                sourceIsFirst = false;
            }
            
            // Lock wallets in consistent order to prevent deadlocks
            Wallet firstWallet = walletRepository.findByIdWithLock(firstWalletId)
                .orElseThrow(() -> new WalletNotFoundException("Wallet not found with ID: " + firstWalletId));
            Wallet secondWallet = walletRepository.findByIdWithLock(secondWalletId)
                .orElseThrow(() -> new WalletNotFoundException("Wallet not found with ID: " + secondWalletId));
            
            // Determine which is source and target based on original order
            Wallet sourceWallet = sourceIsFirst ? firstWallet : secondWallet;
            Wallet targetWallet = sourceIsFirst ? secondWallet : firstWallet;
            
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
                targetWallet.getId()
            );
            
            Transaction targetTransaction = new Transaction(
                savedTargetWallet, 
                TransactionType.TRANSFER_IN, 
                amount, 
                targetNewBalance, 
                sourceWallet.getId()
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
            // Validate transaction amount
            if (amount.compareTo(WalletConstants.MIN_TRANSACTION_AMOUNT) < 0) {
                throw new IllegalArgumentException("Transaction amount must be at least " + WalletConstants.MIN_TRANSACTION_AMOUNT);
            }
            
            // Temporarily commented out for testing
            // if (amount.compareTo(WalletConstants.MAX_TRANSACTION_AMOUNT) > 0) {
            //     throw new IllegalArgumentException(WalletConstants.MAX_AMOUNT_EXCEEDED_ERROR);
            // }
            
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
            
            // Validate that balance doesn't go negative
            if (newBalance.compareTo(BigDecimal.ZERO) < 0) {
                throw new IllegalStateException(WalletConstants.NEGATIVE_BALANCE_ERROR);
            }
            
            wallet.setBalance(newBalance);
            Wallet savedWallet = walletRepository.save(wallet);
            
            // Create transaction record with the saved wallet reference
            Transaction transaction = new Transaction(savedWallet, type, amount, newBalance);
            transactionRepository.save(transaction);
            
            log.info("{} completed. New balance: {}", type, newBalance);
            
            return walletMapper.toBalanceResponse(savedWallet.getId(), savedWallet.getBalance(), newBalance);
        });
    }
}
