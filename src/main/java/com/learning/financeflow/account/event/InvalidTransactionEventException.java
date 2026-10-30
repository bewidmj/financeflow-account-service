package com.learning.financeflow.account.event;

public class InvalidTransactionEventException extends RuntimeException {

    public InvalidTransactionEventException(String message) {
        super(message);
    }

    public InvalidTransactionEventException(String message, Throwable cause) {
        super(message, cause);
    }
}
