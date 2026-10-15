package com.recsys.exception;

/** The client sent data that failed validation (HTTP 400). */
public class ValidationException extends RecSysException {
    public ValidationException(String message) {
        super(400, "VALIDATION_ERROR", message);
    }
}
