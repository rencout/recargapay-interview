package com.recargapay.walletservice.exception;

public class InvalidTimestampException extends RuntimeException {
    
    public InvalidTimestampException(String message) {
        super(message);
    }
    
    public InvalidTimestampException(String message, Throwable cause) {
        super(message, cause);
    }
}
