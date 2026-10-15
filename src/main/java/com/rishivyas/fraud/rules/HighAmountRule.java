package com.rishivyas.fraud.rules;

import com.rishivyas.fraud.config.FraudProperties;
import com.rishivyas.fraud.model.RuleResult;
import com.rishivyas.fraud.model.Transaction;
import com.rishivyas.fraud.state.CustomerSnapshot;
import org.springframework.stereotype.Component;

import java.util.Optional;

/** Flags transactions above an absolute amount, regardless of customer history. */
@Component
public class HighAmountRule implements FraudRule {

    static final int SCORE = 30;
    private final FraudProperties.Rules config;

    public HighAmountRule(FraudProperties properties) {
        this.config = properties.rules();
    }

    @Override
    public String name() {
        return "HIGH_AMOUNT";
    }

    @Override
    public Optional<RuleResult> evaluate(Transaction txn, CustomerSnapshot history) {
        if (txn.amount().compareTo(config.highAmount()) >= 0) {
            return hit(SCORE, "Amount %s %s is at or above the %s limit"
                    .formatted(txn.amount(), txn.currency(), config.highAmount()));
        }
        return Optional.empty();
    }
}
