package com.liftcare.exception;

public class LiftCareException extends RuntimeException {
    public LiftCareException(String message) {
        super(message);
    }

    public LiftCareException(String message, Throwable cause) {
        super(message, cause);
    }
}
