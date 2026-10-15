package com.recsys.exception;

/** The caller is signed in but not allowed to do this (HTTP 403). */
public class ForbiddenException extends RecSysException {
    public ForbiddenException(String message) {
        super(403, "FORBIDDEN", message);
    }
}
