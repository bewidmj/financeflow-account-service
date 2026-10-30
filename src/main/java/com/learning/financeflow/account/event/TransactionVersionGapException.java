package com.learning.financeflow.account.event;

public class TransactionVersionGapException extends RuntimeException {

    public TransactionVersionGapException(String message) {
        super(message);
    }
}
