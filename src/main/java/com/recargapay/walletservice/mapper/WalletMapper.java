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
        
        return WalletResponse.builder()
                .id(wallet.getId())
                .userId(wallet.getUserId())
                .balance(wallet.getBalance())
                .createdAt(wallet.getCreatedAt())
                .updatedAt(wallet.getUpdatedAt())
                .build();
    }
    
    /**
     * Creates a BalanceResponse with wallet ID and balance
     */
    public BalanceResponse toBalanceResponse(UUID walletId, java.math.BigDecimal balance) {
        if (walletId == null) {
            return null;
        }
        
        return BalanceResponse.builder()
                .walletId(walletId)
                .balance(balance)
                .build();
    }
}

