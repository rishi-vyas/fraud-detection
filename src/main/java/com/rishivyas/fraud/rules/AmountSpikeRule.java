package com.rishivyas.fraud.rules;

import com.rishivyas.fraud.config.FraudProperties;
import com.rishivyas.fraud.model.RuleResult;
import com.rishivyas.fraud.model.Transaction;
import com.rishivyas.fraud.state.CustomerSnapshot;
import org.springframework.stereotype.Component;

import java.util.Optional;

/** Flags an amount far above this customer's own average, once there is enough history to trust it. */
@Component
public class AmountSpikeRule implements FraudRule {

    static final int SCORE = 35;
    private final FraudProperties.Rules config;

    public AmountSpikeRule(FraudProperties properties) {
        this.config = properties.rules();
    }

    @Override
    public String name() {
        return "AMOUNT_SPIKE";
    }

    @Override
    public Optional<RuleResult> evaluate(Transaction txn, CustomerSnapshot history) {
        if (history.acceptedCount() < config.spikeMinHistory()) {
            return Optional.empty();
        }
        double amount = txn.amount().doubleValue();
        double ratio = amount / history.meanAmount();
        if (ratio >= config.spikeMultiplier()) {
            return hit(SCORE, "Amount is %.1fx the customer's average of %.2f"
                    .formatted(ratio, history.meanAmount()));
        }
        return Optional.empty();
    }
}
