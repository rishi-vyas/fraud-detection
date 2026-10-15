package com.rishivyas.fraud;

import com.rishivyas.fraud.config.FraudProperties;
import com.rishivyas.fraud.model.Transaction;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;

public final class TestData {

    public static final Instant T0 = Instant.parse("2026-10-01T10:00:00Z");
    public static final double DUBLIN_LAT = 53.3498, DUBLIN_LON = -6.2603;
    public static final double NEW_YORK_LAT = 40.7128, NEW_YORK_LON = -74.0060;

    private TestData() {
    }

    public static FraudProperties properties() {
        return new FraudProperties(
                new FraudProperties.Scoring(40, 70),
                new FraudProperties.Rules(new BigDecimal("5000"), 5, Duration.ofSeconds(60), 5.0, 5,
                        900, 100, new BigDecimal("1000"), Set.of("GAMBLING", "CRYPTO", "MONEY_TRANSFER", "GIFT_CARDS")),
                new FraudProperties.Topics("transactions", "fraud-decisions", "fraud-alerts", 3));
    }

    public static Transaction txn(String id, String amount, Instant at) {
        return txn(id, "cust-1", amount, "device-1", DUBLIN_LAT, DUBLIN_LON, "GROCERY", at);
    }

    public static Transaction txn(String id, String customer, String amount, String device,
                                  double lat, double lon, String category, Instant at) {
        return new Transaction(id, customer, new BigDecimal(amount), "EUR", "merchant-1", category, device, lat, lon, at);
    }
}
