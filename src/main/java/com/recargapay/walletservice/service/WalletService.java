package com.recargapay.walletservice.service;

import com.recargapay.walletservice.dto.BalanceResponse;
import com.recargapay.walletservice.entity.Transaction;
import com.recargapay.walletservice.entity.TransactionType;
import com.recargapay.walletservice.entity.Wallet;
import com.recargapay.walletservice.exception.WalletNotFoundException;
import com.recargapay.walletservice.mapper.WalletMapper;
import com.recargapay.walletservice.repository.TransactionRepository;
import com.recargapay.walletservice.repository.WalletRepository;
import com.recargapay.walletservice.util.ValidationUtils;
import com.recargapay.walletservice.util.WalletConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
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
        return walletMapper.toBalanceResponse(wallet);
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
            return walletMapper.toBalanceResponse(savedWallet.getId(), savedWallet.getBalance(), newBalance);
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
            return walletMapper.toBalanceResponse(savedWallet.getId(), savedWallet.getBalance(), newBalance);
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
            
            // Find wallets in the correct order to prevent deadlocks
            Wallet firstWallet = findWalletById(firstWalletId);
            Wallet secondWallet = findWalletById(secondWalletId);
            
            Wallet sourceWallet = sourceIsFirst ? firstWallet : secondWallet;
            Wallet targetWallet = sourceIsFirst ? secondWallet : firstWallet;
            
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
        for (int attempt = 0; attempt < WalletConstants.MAX_RETRIES; attempt++) {
            try {
                return operation.get();
            } catch (ObjectOptimisticLockingFailureException e) {
                handleOptimisticLockingFailure(attempt, e);
            }
        }
        throw new RuntimeException("Max retries reached for optimistic locking failure");
    }

    private void handleOptimisticLockingFailure(int attempt, ObjectOptimisticLockingFailureException e) {
        if (attempt >= WalletConstants.MAX_RETRIES - 1) {
            log.error("Max retries reached for optimistic locking failure", e);
            throw e;
        }
        log.warn("Optimistic locking failure, retrying... (attempt {}/{})", attempt + 1, WalletConstants.MAX_RETRIES);
        sleepWithInterruptHandling(WalletConstants.RETRY_DELAY_MS * (attempt + 1));
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




}
