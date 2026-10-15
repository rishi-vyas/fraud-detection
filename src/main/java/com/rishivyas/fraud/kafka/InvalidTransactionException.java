package com.rishivyas.fraud.kafka;

/** Thrown for payloads that can never succeed; routed straight to the DLT without retries. */
public class InvalidTransactionException extends RuntimeException {

    public InvalidTransactionException(String message) {
        super(message);
    }
}
