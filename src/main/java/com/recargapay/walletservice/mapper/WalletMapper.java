package com.recargapay.walletservice.mapper;

import com.recargapay.walletservice.dto.BalanceResponse;
import com.recargapay.walletservice.dto.WalletResponse;
import com.recargapay.walletservice.entity.Wallet;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class WalletMapper {
    
    /**
     * Maps a Wallet entity to WalletResponse DTO
     */
    public WalletResponse toResponse(Wallet wallet) {
        if (wallet == null) {
            return null;
        }
        
        WalletResponse response = new WalletResponse();
        response.setId(wallet.getId());
        response.setUserId(wallet.getUserId());
        response.setBalance(wallet.getBalance());
        response.setCreatedAt(wallet.getCreatedAt());
        response.setUpdatedAt(wallet.getUpdatedAt());
        return response;
    }
    
    /**
     * Maps a Wallet entity to BalanceResponse DTO
     */
    public BalanceResponse toBalanceResponse(Wallet wallet) {
        if (wallet == null) {
            return null;
        }
        
        BalanceResponse response = new BalanceResponse();
        response.setWalletId(wallet.getId());
        response.setBalance(wallet.getBalance());
        response.setBalanceAfter(wallet.getBalance());
        return response;
    }
    
    /**
     * Creates a BalanceResponse with custom balance values
     */
    public BalanceResponse toBalanceResponse(UUID walletId, java.math.BigDecimal balance, java.math.BigDecimal balanceAfter) {
        BalanceResponse response = new BalanceResponse();
        response.setWalletId(walletId);
        response.setBalance(balance);
        response.setBalanceAfter(balanceAfter);
        return response;
    }
}

