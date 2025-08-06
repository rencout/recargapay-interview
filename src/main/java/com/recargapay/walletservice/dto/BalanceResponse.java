package com.recargapay.walletservice.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
public class BalanceResponse {
    private UUID walletId;
    private BigDecimal balance;
    private BigDecimal balanceAfter;
}
