package com.rishivyas.fraud.config;

import com.rishivyas.fraud.model.Decision;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Set;

/**
 * Externalised tuning for the fraud engine. Thresholds live in configuration,
 * not code, so risk teams can tune them per environment without a redeploy of logic.
 */
@ConfigurationProperties(prefix = "fraud")
public record FraudProperties(Scoring scoring, Rules rules, Topics topics) {

    public record Scoring(int reviewThreshold, int declineThreshold) {
    }

    public record Rules(
            BigDecimal highAmount,
            int velocityMaxTransactions,
            Duration velocityWindow,
            double spikeMultiplier,
            int spikeMinHistory,
            double maxTravelSpeedKmh,
            double minTravelDistanceKm,
            BigDecimal newDeviceAmount,
            Set<String> highRiskCategories) {
    }

    public record Topics(String transactions, String decisions, String alerts, int partitions) {

        public String deadLetter() {
            return transactions + ".DLT";
        }
    }

    public Decision decide(int riskScore) {
        if (riskScore >= scoring.declineThreshold()) {
            return Decision.DECLINE;
        }
        if (riskScore >= scoring.reviewThreshold()) {
            return Decision.REVIEW;
        }
        return Decision.APPROVE;
    }
}
