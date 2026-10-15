package com.rishivyas.fraud.rules;

import com.rishivyas.fraud.config.FraudProperties;
import com.rishivyas.fraud.model.RuleResult;
import com.rishivyas.fraud.model.Transaction;
import com.rishivyas.fraud.state.CustomerSnapshot;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

/**
 * Flags a transaction whose location is unreachable from the previous one in the elapsed time
 * (implied speed faster than a commercial flight), a strong signal of a cloned card.
 */
@Component
public class ImpossibleTravelRule implements FraudRule {

    static final int SCORE = 50;
    private static final double EARTH_RADIUS_KM = 6371.0;
    private final FraudProperties.Rules config;

    public ImpossibleTravelRule(FraudProperties properties) {
        this.config = properties.rules();
    }

    @Override
    public String name() {
        return "IMPOSSIBLE_TRAVEL";
    }

    @Override
    public Optional<RuleResult> evaluate(Transaction txn, CustomerSnapshot history) {
        Transaction previous = history.lastAccepted();
        if (previous == null) {
            return Optional.empty();
        }
        double km = haversineKm(previous.latitude(), previous.longitude(), txn.latitude(), txn.longitude());
        if (km < config.minTravelDistanceKm()) {
            return Optional.empty();
        }
        double hours = Math.abs(Duration.between(previous.timestamp(), txn.timestamp()).toMillis()) / 3_600_000.0;
        if (hours == 0) {
            return hit(SCORE, "%.0f km from previous transaction at the same instant".formatted(km));
        }
        double speedKmh = km / hours;
        if (speedKmh > config.maxTravelSpeedKmh()) {
            return hit(SCORE, "%.0f km from previous transaction in %.1f min (implied %.0f km/h)"
                    .formatted(km, hours * 60, speedKmh));
        }
        return Optional.empty();
    }

    static double haversineKm(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 2 * EARTH_RADIUS_KM * Math.asin(Math.sqrt(a));
    }
}
