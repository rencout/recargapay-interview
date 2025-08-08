package com.recargapay.walletservice.service;

import com.recargapay.walletservice.dto.BalanceResponse;
import com.recargapay.walletservice.entity.Transaction;
import com.recargapay.walletservice.entity.TransactionType;
import com.recargapay.walletservice.entity.Wallet;
import com.recargapay.walletservice.exception.FutureTimestampException;
import com.recargapay.walletservice.exception.InsufficientFundsException;
import com.recargapay.walletservice.exception.WalletNotFoundException;
import com.recargapay.walletservice.mapper.WalletMapper;
import com.recargapay.walletservice.repository.TransactionRepository;
import com.recargapay.walletservice.repository.WalletRepository;
import com.recargapay.walletservice.util.MoneyUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WalletServiceTest {

    @Mock
    private WalletRepository walletRepository;

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private WalletMapper walletMapper;

    @InjectMocks
    private WalletService walletService;

    private UUID walletId;
    private Wallet wallet;
    private String userId;

    @BeforeEach
    void setUp() {
        walletId = UUID.randomUUID();
        userId = "user123";
        wallet = new Wallet(userId);
        wallet.setId(walletId);
        wallet.setBalance(BigDecimal.valueOf(100.00));
        wallet.setCreatedAt(LocalDateTime.now().minusDays(1));
    }

    @Test
    void createWallet_Success() {
        // Given
        Wallet newWallet = new Wallet(userId);
        newWallet.setId(walletId);
        newWallet.setBalance(BigDecimal.ZERO);
        when(walletRepository.save(any(Wallet.class))).thenReturn(newWallet);

        // When
        Wallet result = walletService.createWallet(userId);

        // Then
        assertNotNull(result);
        assertEquals(userId, result.getUserId());
        assertEquals(BigDecimal.ZERO, result.getBalance());
        verify(walletRepository).save(any(Wallet.class));
    }

    @Test
    void getCurrentBalance_Success() {
        // Given
        when(walletRepository.findById(walletId)).thenReturn(Optional.of(wallet));
        BalanceResponse expectedResponse = BalanceResponse.builder()
                .walletId(walletId)
                .balance(MoneyUtils.format(BigDecimal.valueOf(100.00)))
                .build();
        when(walletMapper.toBalanceResponse(any(), any())).thenReturn(expectedResponse);

        // When
        BalanceResponse result = walletService.getCurrentBalance(walletId);

        // Then
        assertNotNull(result);
        assertEquals(walletId, result.getWalletId());
        assertEquals(MoneyUtils.format(BigDecimal.valueOf(100.00)), result.getBalance());
    }

    @Test
    void getCurrentBalance_WalletNotFound() {
        // Given
        when(walletRepository.findById(walletId)).thenReturn(Optional.empty());

        // When & Then
        assertThrows(WalletNotFoundException.class, () -> walletService.getCurrentBalance(walletId));
    }

    @Test
    void getHistoricalBalance_Success() {
        // Given
        LocalDateTime timestamp = LocalDateTime.now();
        Transaction transaction = new Transaction(wallet, TransactionType.DEPOSIT, BigDecimal.valueOf(50.00), BigDecimal.valueOf(150.00));
        when(walletRepository.findById(walletId)).thenReturn(Optional.of(wallet));
        when(transactionRepository.findLastTransactionBeforeOrAt(walletId, timestamp))
                .thenReturn(Optional.of(transaction));

        // When
        BigDecimal result = walletService.getHistoricalBalance(walletId, timestamp);

        // Then
        assertEquals(BigDecimal.valueOf(150.00), result);
        verify(walletRepository).findById(walletId);
        verify(transactionRepository).findLastTransactionBeforeOrAt(walletId, timestamp);
    }

    @Test
    void getHistoricalBalance_FutureTimestamp() {
        // Given
        LocalDateTime futureTimestamp = LocalDateTime.now().plusDays(1);

        // When & Then
        assertThrows(FutureTimestampException.class, () -> walletService.getHistoricalBalance(walletId, futureTimestamp));
    }

    @Test
    void deposit_Success() {
        // Given
        BigDecimal amount = BigDecimal.valueOf(50.00);
        when(walletRepository.findById(walletId)).thenReturn(Optional.of(wallet));
        when(walletRepository.save(any(Wallet.class))).thenReturn(wallet);
        when(transactionRepository.save(any(Transaction.class))).thenReturn(new Transaction());
        
        BalanceResponse expectedResponse = BalanceResponse.builder()
                .walletId(walletId)
                .balance(MoneyUtils.format(BigDecimal.valueOf(150.00)))
                .build();
        when(walletMapper.toBalanceResponse(any(), any())).thenReturn(expectedResponse);

        // When
        BalanceResponse result = walletService.deposit(walletId, amount);

        // Then
        assertNotNull(result);
        assertEquals(walletId, result.getWalletId());
        assertEquals(MoneyUtils.format(BigDecimal.valueOf(150.00)), result.getBalance());
        verify(walletRepository).save(any(Wallet.class));
        verify(transactionRepository).save(any(Transaction.class));
    }

    @Test
    void withdraw_Success() {
        // Given
        BigDecimal amount = BigDecimal.valueOf(30.00);
        when(walletRepository.findById(walletId)).thenReturn(Optional.of(wallet));
        when(walletRepository.save(any(Wallet.class))).thenReturn(wallet);
        when(transactionRepository.save(any(Transaction.class))).thenReturn(new Transaction());
        
        BalanceResponse expectedResponse = BalanceResponse.builder()
                .walletId(walletId)
                .balance(MoneyUtils.format(BigDecimal.valueOf(70.00)))
                .build();
        when(walletMapper.toBalanceResponse(any(), any())).thenReturn(expectedResponse);

        // When
        BalanceResponse result = walletService.withdraw(walletId, amount);

        // Then
        assertNotNull(result);
        assertEquals(walletId, result.getWalletId());
        assertEquals(MoneyUtils.format(BigDecimal.valueOf(70.00)), result.getBalance());
        verify(walletRepository).save(any(Wallet.class));
        verify(transactionRepository).save(any(Transaction.class));
    }

    @Test
    void withdraw_InsufficientFunds() {
        // Given
        BigDecimal amount = BigDecimal.valueOf(150.00);
        when(walletRepository.findById(walletId)).thenReturn(Optional.of(wallet));

        // When & Then
        assertThrows(InsufficientFundsException.class, () -> walletService.withdraw(walletId, amount));
        verify(walletRepository, never()).save(any(Wallet.class));
        verify(transactionRepository, never()).save(any(Transaction.class));
    }

    @Test
    void transfer_Success() {
        // Given
        UUID targetWalletId = UUID.randomUUID();
        BigDecimal amount = BigDecimal.valueOf(30.00);
        Wallet targetWallet = new Wallet("user456");
        targetWallet.setId(targetWalletId);
        targetWallet.setBalance(BigDecimal.valueOf(50.00));

        when(walletRepository.findById(walletId)).thenReturn(Optional.of(wallet));
        when(walletRepository.findById(targetWalletId)).thenReturn(Optional.of(targetWallet));
        when(walletRepository.save(any(Wallet.class))).thenReturn(wallet);
        when(transactionRepository.save(any(Transaction.class))).thenReturn(new Transaction());

        // When
        walletService.transfer(walletId, targetWalletId, amount);

        // Then
        verify(walletRepository, times(2)).save(any(Wallet.class));
        verify(transactionRepository, times(2)).save(any(Transaction.class));
    }

    @Test
    void transfer_InsufficientFunds() {
        // Given
        UUID targetWalletId = UUID.randomUUID();
        BigDecimal amount = BigDecimal.valueOf(150.00);
        Wallet targetWallet = new Wallet("user456");
        targetWallet.setId(targetWalletId);

        when(walletRepository.findById(walletId)).thenReturn(Optional.of(wallet));
        when(walletRepository.findById(targetWalletId)).thenReturn(Optional.of(targetWallet));

        // When & Then
        assertThrows(InsufficientFundsException.class, () -> walletService.transfer(walletId, targetWalletId, amount));
        verify(walletRepository, never()).save(any(Wallet.class));
        verify(transactionRepository, never()).save(any(Transaction.class));
    }
}
