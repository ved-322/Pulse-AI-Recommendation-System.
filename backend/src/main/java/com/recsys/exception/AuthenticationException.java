package com.recsys.exception;

/** The caller is not signed in or supplied bad credentials (HTTP 401). */
public class AuthenticationException extends RecSysException {
    public AuthenticationException(String message) {
        super(401, "UNAUTHENTICATED", message);
    }
}
