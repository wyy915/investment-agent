package com.investment.exception;

public class InvestmentException extends RuntimeException {
    public InvestmentException(String message) {
        super(message);
    }

    public InvestmentException(String message, Throwable cause) {
        super(message, cause);
    }
}





