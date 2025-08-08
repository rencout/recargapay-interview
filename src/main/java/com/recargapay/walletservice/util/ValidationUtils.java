package com.recargapay.walletservice.util;

import com.recargapay.walletservice.entity.Wallet;
import com.recargapay.walletservice.exception.FutureTimestampException;
import com.recargapay.walletservice.exception.InsufficientFundsException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.function.Predicate;

public final class ValidationUtils {

    private ValidationUtils() {
        // Utility class - prevent instantiation
    }

    public static void validateTimestampNotInFuture(LocalDateTime timestamp) {
        validateCondition(
            timestamp,
            t -> t.isAfter(LocalDateTime.now()),
            t -> new FutureTimestampException("Cannot retrieve historical balance for future timestamp: " + t)
        );
    }

    public static void validateSufficientFunds(Wallet wallet, BigDecimal amount) {
        validateCondition(
            wallet,
            w -> w.getBalance().compareTo(amount) < 0,
            w -> new InsufficientFundsException(
                String.format("Insufficient funds. Current balance: %s, requested amount: %s", 
                    w.getBalance(), amount)
            )
        );
    }

    public static void validateTransferRequest(UUID sourceWalletId, UUID targetWalletId) {
        validateCondition(
            sourceWalletId,
            id -> id.equals(targetWalletId),
            id -> new IllegalArgumentException("Source and target wallets cannot be the same")
        );
    }

    private static <T> void validateCondition(T value, Predicate<T> condition, java.util.function.Function<T, RuntimeException> exceptionSupplier) {
        if (condition.test(value)) {
            throw exceptionSupplier.apply(value);
        }
    }
}
