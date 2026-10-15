package com.rishivyas.fraud.rules;

import com.rishivyas.fraud.model.RuleResult;
import com.rishivyas.fraud.model.Transaction;
import com.rishivyas.fraud.state.CustomerSnapshot;

import java.util.Optional;

/**
 * A single, stateless fraud signal. New rules are added by creating another Spring
 * {@code @Component} implementing this interface; the scoring service picks them up automatically.
 */
public interface FraudRule {

    String name();

    Optional<RuleResult> evaluate(Transaction txn, CustomerSnapshot history);

    default Optional<RuleResult> hit(int score, String reason) {
        return Optional.of(new RuleResult(name(), score, reason));
    }
}
