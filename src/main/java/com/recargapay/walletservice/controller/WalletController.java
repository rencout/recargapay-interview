package com.recargapay.walletservice.controller;

import com.recargapay.walletservice.dto.*;
import com.recargapay.walletservice.entity.Wallet;
import com.recargapay.walletservice.service.WalletService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@RestController
@RequestMapping("/api/wallets")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Wallet Management", description = "APIs for managing wallets and transactions")
public class WalletController {

    private final WalletService walletService;

    @PostMapping
    @Operation(summary = "Create a new wallet", description = "Creates a new wallet for the specified user")
    public ResponseEntity<WalletResponse> createWallet(@Valid @RequestBody CreateWalletRequest request) {
        log.info("Creating wallet for user: {}", request.getUserId());
        Wallet wallet = walletService.createWallet(request.getUserId());
        
        WalletResponse response = new WalletResponse();
        response.setId(wallet.getId());
        response.setUserId(wallet.getUserId());
        response.setBalance(wallet.getBalance());
        response.setCreatedAt(wallet.getCreatedAt());
        response.setUpdatedAt(wallet.getUpdatedAt());
        
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{walletId}/balance")
    @Operation(summary = "Get current balance", description = "Retrieves the current balance of a wallet")
    public ResponseEntity<BalanceResponse> getCurrentBalance(
            @Parameter(description = "Wallet ID") @PathVariable UUID walletId) {
        log.info("Getting current balance for wallet: {}", walletId);
        BalanceResponse response = walletService.getCurrentBalance(walletId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{walletId}/balance/history")
    @Operation(summary = "Get historical balance", description = "Retrieves the balance of a wallet at a specific point in time")
    public ResponseEntity<BalanceResponse> getHistoricalBalance(
            @Parameter(description = "Wallet ID") @PathVariable UUID walletId,
            @Parameter(description = "Timestamp for historical balance (ISO format)") 
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime timestamp) {
        log.info("Getting historical balance for wallet: {} at timestamp: {}", walletId, timestamp);
        
        BigDecimal historicalBalance = walletService.getHistoricalBalance(walletId, timestamp);
        
        BalanceResponse response = new BalanceResponse();
        response.setWalletId(walletId);
        response.setBalance(historicalBalance);
        response.setBalanceAfter(historicalBalance);
        
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{walletId}/deposit")
    @Operation(summary = "Deposit funds", description = "Deposits funds into a wallet")
    public ResponseEntity<BalanceResponse> deposit(
            @Parameter(description = "Wallet ID") @PathVariable UUID walletId,
            @Valid @RequestBody TransactionRequest request) {
        log.info("Processing deposit request for wallet: {} with amount: {}", walletId, request.getAmount());
        BalanceResponse response = walletService.deposit(walletId, request.getAmount());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{walletId}/withdraw")
    @Operation(summary = "Withdraw funds", description = "Withdraws funds from a wallet")
    public ResponseEntity<BalanceResponse> withdraw(
            @Parameter(description = "Wallet ID") @PathVariable UUID walletId,
            @Valid @RequestBody TransactionRequest request) {
        log.info("Processing withdrawal request for wallet: {} with amount: {}", walletId, request.getAmount());
        BalanceResponse response = walletService.withdraw(walletId, request.getAmount());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/transfer")
    @Operation(summary = "Transfer funds", description = "Transfers funds between two wallets")
    public ResponseEntity<Void> transfer(@Valid @RequestBody TransferRequest request) {
        log.info("Processing transfer request from wallet: {} to wallet: {} with amount: {}", 
                request.getSourceWalletId(), request.getTargetWalletId(), request.getAmount());
        
        walletService.transfer(request.getSourceWalletId(), request.getTargetWalletId(), request.getAmount());
        return ResponseEntity.ok().build();
    }
}
