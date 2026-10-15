package com.recsys.exception;

/** Rate limiting kicked in, e.g. too many failed logins (HTTP 429). */
public class TooManyRequestsException extends RecSysException {
    public TooManyRequestsException(String message) {
        super(429, "TOO_MANY_REQUESTS", message);
    }
}
