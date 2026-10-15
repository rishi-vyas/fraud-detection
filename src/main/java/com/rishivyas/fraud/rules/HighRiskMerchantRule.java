package com.rishivyas.fraud.rules;

import com.rishivyas.fraud.config.FraudProperties;
import com.rishivyas.fraud.model.RuleResult;
import com.rishivyas.fraud.model.Transaction;
import com.rishivyas.fraud.state.CustomerSnapshot;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Optional;

/** Adds a small amount of risk for merchant categories that are popular cash-out channels. */
@Component
public class HighRiskMerchantRule implements FraudRule {

    static final int SCORE = 15;
    private final FraudProperties.Rules config;

    public HighRiskMerchantRule(FraudProperties properties) {
        this.config = properties.rules();
    }

    @Override
    public String name() {
        return "HIGH_RISK_MERCHANT";
    }

    @Override
    public Optional<RuleResult> evaluate(Transaction txn, CustomerSnapshot history) {
        String category = txn.merchantCategory().toUpperCase(Locale.ROOT);
        if (config.highRiskCategories().contains(category)) {
            return hit(SCORE, "Merchant category %s is high risk".formatted(category));
        }
        return Optional.empty();
    }
}
