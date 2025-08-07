package com.recargapay.walletservice.service;

import com.recargapay.walletservice.dto.BalanceResponse;
import com.recargapay.walletservice.entity.Transaction;
import com.recargapay.walletservice.entity.TransactionType;
import com.recargapay.walletservice.entity.Wallet;
import com.recargapay.walletservice.exception.InsufficientFundsException;
import com.recargapay.walletservice.exception.WalletNotFoundException;
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
        
        // Find the last transaction before or at the given timestamp
        var lastTransaction = transactionRepository.findLastTransactionBeforeOrAt(walletId, timestamp);
        
        if (lastTransaction.isPresent()) {
            BigDecimal balance = MoneyUtils.format(lastTransaction.get().getBalanceAfter());
            log.info("Historical balance for wallet {} at {}: {}", walletId, timestamp, balance);
            return balance;
        } else {
            // If no transaction found, calculate balance from all transactions up to timestamp
            BigDecimal calculatedBalance = MoneyUtils.format(transactionRepository.sumTransactionsUpTo(walletId, timestamp));
            log.info("No transaction found, calculated balance: {}", calculatedBalance);
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
        
        executeWithRetry(() -> {
            Wallet sourceWallet = findWalletById(sourceWalletId);
            Wallet targetWallet = findWalletById(targetWalletId);
            
            if (sourceWallet.getBalance().compareTo(amount) < 0) {
                throw new InsufficientFundsException(
                    String.format(WalletConstants.INSUFFICIENT_FUNDS_SOURCE_FORMAT, 
                        sourceWallet.getBalance(), amount)
                );
            }
            
            // Update source wallet
            BigDecimal sourceNewBalance = MoneyUtils.subtract(sourceWallet.getBalance(), amount);
            sourceWallet.setBalance(sourceNewBalance);
            walletRepository.save(sourceWallet);
            
            // Update target wallet
            BigDecimal targetNewBalance = MoneyUtils.add(targetWallet.getBalance(), amount);
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
