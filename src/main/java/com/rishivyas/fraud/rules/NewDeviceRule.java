package com.rishivyas.fraud.rules;

import com.rishivyas.fraud.config.FraudProperties;
import com.rishivyas.fraud.model.RuleResult;
import com.rishivyas.fraud.model.Transaction;
import com.rishivyas.fraud.state.CustomerSnapshot;
import org.springframework.stereotype.Component;

import java.util.Optional;

/** Flags a sizeable payment from a device this (established) customer has never used before. */
@Component
public class NewDeviceRule implements FraudRule {

    static final int SCORE = 25;
    private final FraudProperties.Rules config;

    public NewDeviceRule(FraudProperties properties) {
        this.config = properties.rules();
    }

    @Override
    public String name() {
        return "NEW_DEVICE";
    }

    @Override
    public Optional<RuleResult> evaluate(Transaction txn, CustomerSnapshot history) {
        boolean established = history.acceptedCount() > 0;
        boolean unknownDevice = !history.knownDevices().contains(txn.deviceId());
        boolean significant = txn.amount().compareTo(config.newDeviceAmount()) >= 0;
        if (established && unknownDevice && significant) {
            return hit(SCORE, "Unrecognised device %s for a payment of %s %s"
                    .formatted(txn.deviceId(), txn.amount(), txn.currency()));
        }
        return Optional.empty();
    }
}
