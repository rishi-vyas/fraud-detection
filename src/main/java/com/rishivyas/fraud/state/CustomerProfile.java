package com.rishivyas.fraud.state;

import com.rishivyas.fraud.model.Transaction;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Mutable per-customer behavioural state. Not thread-safe on its own: callers
 * synchronise on the instance (see {@code FraudScoringService}).
 *
 * <p>Memory is bounded per customer: a fixed window of recent attempt timestamps,
 * a capped device set, and a running mean (Welford) instead of the full amount history.
 */
public class CustomerProfile {

    static final int MAX_RECENT_ATTEMPTS = 100;
    static final int MAX_KNOWN_DEVICES = 20;

    private long acceptedCount;
    private double meanAmount;
    private final Set<String> knownDevices = new LinkedHashSet<>();
    private final Deque<Instant> recentAttempts = new ArrayDeque<>();
    private Transaction lastAccepted;

    public CustomerSnapshot snapshot() {
        return new CustomerSnapshot(acceptedCount, meanAmount, Set.copyOf(knownDevices),
                List.copyOf(recentAttempts), lastAccepted);
    }

    /** Every attempt counts toward velocity, including declined ones (card-testing bursts). */
    public void recordAttempt(Transaction txn) {
        recentAttempts.addLast(txn.timestamp());
        while (recentAttempts.size() > MAX_RECENT_ATTEMPTS) {
            recentAttempts.removeFirst();
        }
    }

    /**
     * Only non-declined transactions update the baseline. Otherwise a fraudster's device
     * would become "known" and their location would become the reference point.
     */
    public void recordAccepted(Transaction txn) {
        recordAttempt(txn);
        acceptedCount++;
        meanAmount += (txn.amount().doubleValue() - meanAmount) / acceptedCount;

        if (!knownDevices.contains(txn.deviceId())) {
            if (knownDevices.size() >= MAX_KNOWN_DEVICES) {
                Iterator<String> oldest = knownDevices.iterator();
                oldest.next();
                oldest.remove();
            }
            knownDevices.add(txn.deviceId());
        }
        if (lastAccepted == null || !txn.timestamp().isBefore(lastAccepted.timestamp())) {
            lastAccepted = txn;
        }
    }
}
