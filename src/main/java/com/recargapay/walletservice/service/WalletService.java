package com.recargapay.walletservice.service;

import com.recargapay.walletservice.dto.BalanceResponse;
import com.recargapay.walletservice.entity.Transaction;
import com.recargapay.walletservice.entity.TransactionType;
import com.recargapay.walletservice.entity.Wallet;
import com.recargapay.walletservice.exception.WalletNotFoundException;
import com.recargapay.walletservice.exception.InvalidTimestampException;
import com.recargapay.walletservice.mapper.WalletMapper;
import com.recargapay.walletservice.repository.TransactionRepository;
import com.recargapay.walletservice.repository.WalletRepository;
import com.recargapay.walletservice.util.ValidationUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.function.Supplier;

@Service
@RequiredArgsConstructor
@Slf4j
public class WalletService {

    private final WalletRepository walletRepository;
    private final TransactionRepository transactionRepository;
    private static final int MAX_RETRIES = 3;
    private static final int RETRY_DELAY_MS = 100;

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
        return createBalanceResponse(wallet, wallet.getBalance());
    }

    @Transactional(readOnly = true)
    public BigDecimal getHistoricalBalance(UUID walletId, LocalDateTime timestamp) {
        log.info("Getting historical balance for wallet: {} at timestamp: {}", walletId, timestamp);
        ValidationUtils.validateTimestampNotInFuture(timestamp);
        findWalletById(walletId);
        
        return transactionRepository.findLastTransactionBeforeOrAt(walletId, timestamp)
                .map(Transaction::getBalanceAfter)
                .orElse(BigDecimal.ZERO);
    }

    @Transactional
    public BalanceResponse deposit(UUID walletId, BigDecimal amount) {
        log.info("Processing deposit of {} for wallet: {}", amount, walletId);
        return executeWithRetry(() -> {
            Wallet wallet = findWalletById(walletId);
            BigDecimal newBalance = wallet.getBalance().add(amount);
            
            wallet.setBalance(newBalance);
            Wallet savedWallet = walletRepository.save(wallet);
            
            createTransaction(wallet, TransactionType.DEPOSIT, amount, newBalance);
            
            log.info("Deposit completed. New balance: {}", newBalance);
            return createBalanceResponse(savedWallet, newBalance);
        });
    }

    @Transactional
    public BalanceResponse withdraw(UUID walletId, BigDecimal amount) {
        log.info("Processing withdrawal of {} for wallet: {}", amount, walletId);
        return executeWithRetry(() -> {
            Wallet wallet = findWalletById(walletId);
            ValidationUtils.validateSufficientFunds(wallet, amount);
            
            BigDecimal newBalance = wallet.getBalance().subtract(amount);
            wallet.setBalance(newBalance);
            Wallet savedWallet = walletRepository.save(wallet);
            
            createTransaction(wallet, TransactionType.WITHDRAW, amount, newBalance);
            
            log.info("Withdrawal completed. New balance: {}", newBalance);
            return createBalanceResponse(savedWallet, newBalance);
        });
    }

    @Transactional(rollbackFor = Exception.class)
    public void transfer(UUID sourceWalletId, UUID targetWalletId, BigDecimal amount) {
        log.info("Processing transfer of {} from wallet {} to wallet {}", amount, sourceWalletId, targetWalletId);
        ValidationUtils.validateTransferRequest(sourceWalletId, targetWalletId);
        
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
            
            ValidationUtils.validateSufficientFunds(sourceWallet, amount);
            
            performTransfer(sourceWallet, targetWallet, amount);

            return null;
        });
    }

    private Wallet findWalletById(UUID walletId) {
        return walletRepository.findById(walletId)
            .orElseThrow(() -> new WalletNotFoundException("Wallet not found with ID: " + walletId));
    }

    private <T> T executeWithRetry(Supplier<T> operation) {
        for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
            try {
                return operation.get();
            } catch (ObjectOptimisticLockingFailureException e) {
                handleOptimisticLockingFailure(attempt, e);
            }
        }
        throw new RuntimeException("Max retries reached for optimistic locking failure");
    }

    private void handleOptimisticLockingFailure(int attempt, ObjectOptimisticLockingFailureException e) {
        if (attempt >= MAX_RETRIES - 1) {
            log.error("Max retries reached for optimistic locking failure", e);
            throw e;
        }
        log.warn("Optimistic locking failure, retrying... (attempt {}/{})", attempt + 1, MAX_RETRIES);
        sleepWithInterruptHandling(RETRY_DELAY_MS * (attempt + 1));
    }

    private void sleepWithInterruptHandling(long delayMs) {
        try {
            Thread.sleep(delayMs);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Thread interrupted during retry", ie);
        }
    }



    private void performTransfer(Wallet sourceWallet, Wallet targetWallet, BigDecimal amount) {
        BigDecimal sourceNewBalance = sourceWallet.getBalance().subtract(amount);
        BigDecimal targetNewBalance = targetWallet.getBalance().add(amount);
        
        sourceWallet.setBalance(sourceNewBalance);
        targetWallet.setBalance(targetNewBalance);
        
        walletRepository.save(sourceWallet);
        walletRepository.save(targetWallet);
        
        createTransferTransactions(sourceWallet, targetWallet, amount, sourceNewBalance, targetNewBalance);
        
        log.info("Transfer completed. Source balance: {}, Target balance: {}", sourceNewBalance, targetNewBalance);
    }

    private void createTransaction(Wallet wallet, TransactionType type, BigDecimal amount, BigDecimal balanceAfter) {
        Transaction transaction = new Transaction(wallet, type, amount, balanceAfter);
        transactionRepository.save(transaction);
    }

    private void createTransferTransactions(Wallet sourceWallet, Wallet targetWallet, BigDecimal amount, 
                                          BigDecimal sourceNewBalance, BigDecimal targetNewBalance) {
        Transaction sourceTransaction = new Transaction(sourceWallet, TransactionType.TRANSFER_OUT, 
                                                      amount, sourceNewBalance, targetWallet.getId());
        Transaction targetTransaction = new Transaction(targetWallet, TransactionType.TRANSFER_IN, 
                                                      amount, targetNewBalance, sourceWallet.getId());
        
        transactionRepository.save(sourceTransaction);
        transactionRepository.save(targetTransaction);
    }

    private BalanceResponse createBalanceResponse(Wallet wallet, BigDecimal balanceAfter) {
        return BalanceResponse.builder()
                .walletId(wallet.getId())
                .balance(wallet.getBalance())
                .balanceAfter(balanceAfter)
                .build();
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
