package com.rishivyas.fraud.state;

import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * In-memory profile store. Because transactions are keyed by customerId, Kafka routes every
 * event for a customer to the same partition, so one consumer thread owns that customer's
 * state. For multi-instance deployments this would move to a Kafka Streams state store or Redis.
 */
@Component
public class CustomerProfileStore {

    private final ConcurrentMap<String, CustomerProfile> profiles = new ConcurrentHashMap<>();

    public CustomerProfile profileFor(String customerId) {
        return profiles.computeIfAbsent(customerId, id -> new CustomerProfile());
    }

    public int size() {
        return profiles.size();
    }
}
