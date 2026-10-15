package com.recsys.exception;

/** Wraps checked {@link java.sql.SQLException}s so upper layers stay free of JDBC (HTTP 500). */
public class DataAccessException extends RecSysException {
    public DataAccessException(String message, Throwable cause) {
        super(500, "DATABASE_ERROR", message, cause);
    }
}
