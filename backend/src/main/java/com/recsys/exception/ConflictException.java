package com.recsys.exception;

/** The request conflicts with current state, e.g. duplicate e-mail (HTTP 409). */
public class ConflictException extends RecSysException {
    public ConflictException(String message) {
        super(409, "CONFLICT", message);
    }
}
