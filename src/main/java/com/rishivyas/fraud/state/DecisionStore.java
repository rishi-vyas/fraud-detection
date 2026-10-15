package com.rishivyas.fraud.state;

import com.rishivyas.fraud.model.FraudDecision;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Bounded LRU cache of recent decisions keyed by transactionId. Serves two purposes:
 * idempotency (Kafka is at-least-once, so redelivered events must not be double-counted)
 * and the read API.
 */
@Component
public class DecisionStore {

    private final LinkedHashMap<String, FraudDecision> decisions;

    public DecisionStore(@Value("${fraud.decision-cache-size:100000}") int capacity) {
        this.decisions = new LinkedHashMap<>(16, 0.75f, false) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, FraudDecision> eldest) {
                return size() > capacity;
            }
        };
    }

    public synchronized void put(FraudDecision decision) {
        decisions.put(decision.transactionId(), decision);
    }

    public synchronized Optional<FraudDecision> find(String transactionId) {
        return Optional.ofNullable(decisions.get(transactionId));
    }

    public synchronized List<FraudDecision> recent(int limit) {
        return decisions.reversed().values().stream().limit(limit).toList();
    }

    public synchronized int size() {
        return decisions.size();
    }
}
