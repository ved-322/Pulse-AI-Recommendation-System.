package com.recsys.exception;

/**
 * Root of the application's exception hierarchy. Every subclass carries the HTTP status
 * and a stable machine-readable code so the web layer can translate it into a JSON error.
 */
public class RecSysException extends RuntimeException {
    private final int status;
    private final String code;

    public RecSysException(int status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public RecSysException(int status, String code, String message, Throwable cause) {
        super(message, cause);
        this.status = status;
        this.code = code;
    }

    public int getStatus() { return status; }

    public String getCode() { return code; }
}
