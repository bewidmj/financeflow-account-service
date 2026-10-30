package com.learning.financeflow.account.event;

public class AccountEventRejectedException extends RuntimeException {

    public AccountEventRejectedException(String message) {
        super(message);
    }

    public AccountEventRejectedException(String message, Throwable cause) {
        super(message, cause);
    }
}
