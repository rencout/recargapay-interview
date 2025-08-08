package com.recargapay.walletservice.dto;

import com.recargapay.walletservice.util.WalletConstants;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class TransactionRequest {
    
    @NotNull(message = WalletConstants.AMOUNT_REQUIRED_MESSAGE)
    @DecimalMin(value = "0.01", message = WalletConstants.AMOUNT_MIN_MESSAGE)
    private BigDecimal amount;
}
