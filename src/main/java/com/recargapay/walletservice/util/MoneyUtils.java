package com.recargapay.walletservice.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class MoneyUtils {
    
    private static final int SCALE = 2;
    private static final RoundingMode ROUNDING_MODE = RoundingMode.HALF_UP;
    
    /**
     * Formats a BigDecimal amount to 2 decimal places with HALF_UP rounding
     */
    public static BigDecimal format(BigDecimal amount) {
        if (amount == null) {
            return null;
        }
        return amount.setScale(SCALE, ROUNDING_MODE);
    }
    
    /**
     * Returns a zero BigDecimal formatted to 2 decimal places
     */
    public static BigDecimal zero() {
        return BigDecimal.ZERO.setScale(SCALE, ROUNDING_MODE);
    }
    
    /**
     * Adds two BigDecimal amounts and formats the result
     */
    public static BigDecimal add(BigDecimal a, BigDecimal b) {
        if (a == null) a = BigDecimal.ZERO;
        if (b == null) b = BigDecimal.ZERO;
        return format(a.add(b));
    }
    
    /**
     * Subtracts two BigDecimal amounts and formats the result
     */
    public static BigDecimal subtract(BigDecimal a, BigDecimal b) {
        if (a == null) a = BigDecimal.ZERO;
        if (b == null) b = BigDecimal.ZERO;
        return format(a.subtract(b));
    }
    
    private MoneyUtils() {
        // Utility class - prevent instantiation
    }
}

