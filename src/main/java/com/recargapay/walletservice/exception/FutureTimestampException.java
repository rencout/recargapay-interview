package com.recargapay.walletservice.exception;

public class FutureTimestampException extends RuntimeException {
    
    public FutureTimestampException(String message) {
        super(message);
    }
    
    public FutureTimestampException(String message, Throwable cause) {
        super(message, cause);
    }
}
