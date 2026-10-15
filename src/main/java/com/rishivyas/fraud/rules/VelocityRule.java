package com.rishivyas.fraud.rules;

import com.rishivyas.fraud.config.FraudProperties;
import com.rishivyas.fraud.model.RuleResult;
import com.rishivyas.fraud.model.Transaction;
import com.rishivyas.fraud.state.CustomerSnapshot;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;

/**
 * Flags bursts of activity (e.g. card testing). Uses event time, not wall-clock time,
 * so replaying a topic produces the same decisions.
 */
@Component
public class VelocityRule implements FraudRule {

    static final int SCORE = 40;
    private final FraudProperties.Rules config;

    public VelocityRule(FraudProperties properties) {
        this.config = properties.rules();
    }

    @Override
    public String name() {
        return "VELOCITY";
    }

    @Override
    public Optional<RuleResult> evaluate(Transaction txn, CustomerSnapshot history) {
        Instant windowStart = txn.timestamp().minus(config.velocityWindow());
        long inWindow = history.recentAttempts().stream()
                .filter(t -> !t.isBefore(windowStart) && !t.isAfter(txn.timestamp()))
                .count() + 1; // include the current transaction
        if (inWindow > config.velocityMaxTransactions()) {
            return hit(SCORE, "%d transactions within %ds (limit %d)"
                    .formatted(inWindow, config.velocityWindow().toSeconds(), config.velocityMaxTransactions()));
        }
        return Optional.empty();
    }
}
