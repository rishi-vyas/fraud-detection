package com.rishivyas.fraud.model;

/**
 * Result of scoring one transaction. {@code duplicate} is true when the transaction ID
 * was already evaluated (Kafka redelivery), in which case the original decision is returned.
 */
public record Evaluation(FraudDecision decision, boolean duplicate) {
}
