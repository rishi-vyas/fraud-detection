package com.rishivyas.fraud.state;

import com.rishivyas.fraud.model.Transaction;

import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * Immutable view of a customer's behaviour *before* the transaction being scored.
 * Rules only ever see this, so they are pure functions and trivially unit-testable.
 */
public record CustomerSnapshot(
        long acceptedCount,
        double meanAmount,
        Set<String> knownDevices,
        List<Instant> recentAttempts,
        Transaction lastAccepted) {

    public static CustomerSnapshot empty() {
        return new CustomerSnapshot(0, 0.0, Set.of(), List.of(), null);
    }
}
