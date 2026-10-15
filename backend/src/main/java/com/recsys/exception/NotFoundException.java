package com.recsys.exception;

/** The requested resource does not exist (HTTP 404). */
public class NotFoundException extends RecSysException {
    public NotFoundException(String message) {
        super(404, "NOT_FOUND", message);
    }
}
