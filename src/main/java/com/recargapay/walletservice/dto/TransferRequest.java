package com.recargapay.walletservice.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
public class TransferRequest {
    
    @NotNull(message = "Source wallet ID is required")
    private UUID sourceWalletId;
    
    @NotNull(message = "Target wallet ID is required")
    private UUID targetWalletId;
    
    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be greater than 0")
    private BigDecimal amount;
}
