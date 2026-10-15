package com.rishivyas.fraud.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** The outcome published to {@code fraud-decisions} (and {@code fraud-alerts} when not approved). */
public record FraudDecision(
        String transactionId,
        String customerId,
        BigDecimal amount,
        String currency,
        Decision decision,
        int riskScore,
        List<RuleResult> triggeredRules,
        Instant evaluatedAt,
        long processingMicros) {
}
