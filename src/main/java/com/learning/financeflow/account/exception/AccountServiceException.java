package com.learning.financeflow.account.exception;

import org.springframework.http.HttpStatus;

public class AccountServiceException extends RuntimeException {

    private final HttpStatus status;

    public AccountServiceException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
