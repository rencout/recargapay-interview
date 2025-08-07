package com.recargapay.walletservice.util;

import java.math.BigDecimal;

public final class WalletConstants {
    
    // Retry configuration
    public static final int MAX_RETRIES = 3;
    public static final int RETRY_DELAY_MS = 100;
    
    // Money constants
    public static final BigDecimal MIN_TRANSACTION_AMOUNT = BigDecimal.valueOf(0.01);
    public static final BigDecimal DEFAULT_TEST_AMOUNT = BigDecimal.valueOf(100.00);
    
    // Validation messages
    public static final String AMOUNT_REQUIRED_MESSAGE = "Amount is required";
    public static final String AMOUNT_MIN_MESSAGE = "Amount must be greater than 0";
    public static final String USER_ID_REQUIRED_MESSAGE = "User ID is required";
    public static final String SOURCE_WALLET_REQUIRED_MESSAGE = "Source wallet ID is required";
    public static final String TARGET_WALLET_REQUIRED_MESSAGE = "Target wallet ID is required";
    
    // Error messages
    public static final String SAME_WALLET_ERROR = "Source and target wallets cannot be the same";
    public static final String INSUFFICIENT_FUNDS_FORMAT = "Insufficient funds. Current balance: %s, requested amount: %s";
    public static final String INSUFFICIENT_FUNDS_SOURCE_FORMAT = "Insufficient funds in source wallet. Current balance: %s, requested amount: %s";
    
    private WalletConstants() {
        // Utility class - prevent instantiation
    }
}

