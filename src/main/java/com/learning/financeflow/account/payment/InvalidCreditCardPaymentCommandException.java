package com.learning.financeflow.account.payment;

public class InvalidCreditCardPaymentCommandException extends RuntimeException {

    public InvalidCreditCardPaymentCommandException(String message) {
        super(message);
    }

    public InvalidCreditCardPaymentCommandException(String message, Throwable cause) {
        super(message, cause);
    }
}
