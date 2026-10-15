package com.rishivyas.fraud.rules;

import com.rishivyas.fraud.config.FraudProperties;
import com.rishivyas.fraud.model.Transaction;
import com.rishivyas.fraud.state.CustomerSnapshot;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

import static com.rishivyas.fraud.TestData.*;
import static org.assertj.core.api.Assertions.assertThat;

class FraudRulesTest {

    private final FraudProperties props = properties();

    private static CustomerSnapshot history(long count, double mean, Set<String> devices,
                                            List<Instant> attempts, Transaction last) {
        return new CustomerSnapshot(count, mean, devices, attempts, last);
    }

    @Nested
    class HighAmount {
        private final HighAmountRule rule = new HighAmountRule(props);

        @Test
        void firesAtThreshold() {
            assertThat(rule.evaluate(txn("t1", "5000.00", T0), CustomerSnapshot.empty())).isPresent();
        }

        @Test
        void quietBelowThreshold() {
            assertThat(rule.evaluate(txn("t1", "4999.99", T0), CustomerSnapshot.empty())).isEmpty();
        }
    }

    @Nested
    class AmountSpike {
        private final AmountSpikeRule rule = new AmountSpikeRule(props);

        @Test
        void firesWhenFarAboveAverage() {
            var h = history(10, 40.0, Set.of("device-1"), List.of(), null);
            assertThat(rule.evaluate(txn("t1", "250", T0), h)).get()
                    .satisfies(r -> assertThat(r.reason()).contains("6.3x"));
        }

        @Test
        void ignoredWithoutEnoughHistory() {
            var h = history(4, 10.0, Set.of("device-1"), List.of(), null);
            assertThat(rule.evaluate(txn("t1", "900", T0), h)).isEmpty();
        }
    }

    @Nested
    class Velocity {
        private final VelocityRule rule = new VelocityRule(props);

        private List<Instant> attempts(int n, long spacingSeconds) {
            return IntStream.range(0, n).mapToObj(i -> T0.plusSeconds(i * spacingSeconds)).toList();
        }

        @Test
        void sixthTransactionInWindowFires() {
            var h = history(5, 10, Set.of(), attempts(5, 5), null);
            assertThat(rule.evaluate(txn("t6", "2", T0.plusSeconds(30)), h)).isPresent();
        }

        @Test
        void fifthTransactionDoesNot() {
            var h = history(4, 10, Set.of(), attempts(4, 5), null);
            assertThat(rule.evaluate(txn("t5", "2", T0.plusSeconds(30)), h)).isEmpty();
        }

        @Test
        void oldAttemptsFallOutOfWindow() {
            var h = history(5, 10, Set.of(), attempts(5, 5), null);
            assertThat(rule.evaluate(txn("t6", "2", T0.plusSeconds(120)), h)).isEmpty();
        }
    }

    @Nested
    class ImpossibleTravel {
        private final ImpossibleTravelRule rule = new ImpossibleTravelRule(props);
        private final Transaction dublin = txn("t1", "20", T0);

        @Test
        void dublinToNewYorkInTenMinutesFires() {
            var h = history(1, 20, Set.of("device-1"), List.of(T0), dublin);
            var ny = txn("t2", "cust-1", "20", "device-1", NEW_YORK_LAT, NEW_YORK_LON, "GROCERY", T0.plusSeconds(600));
            assertThat(rule.evaluate(ny, h)).isPresent();
        }

        @Test
        void dublinToNewYorkAfterTenHoursIsPlausible() {
            var h = history(1, 20, Set.of("device-1"), List.of(T0), dublin);
            var ny = txn("t2", "cust-1", "20", "device-1", NEW_YORK_LAT, NEW_YORK_LON, "GROCERY", T0.plusSeconds(36_000));
            assertThat(rule.evaluate(ny, h)).isEmpty();
        }

        @Test
        void shortHopsAreIgnored() {
            var h = history(1, 20, Set.of("device-1"), List.of(T0), dublin);
            var nearby = txn("t2", "cust-1", "20", "device-1", 53.40, -6.30, "GROCERY", T0.plusSeconds(1));
            assertThat(rule.evaluate(nearby, h)).isEmpty();
        }

        @Test
        void haversineMatchesKnownDistance() {
            double km = ImpossibleTravelRule.haversineKm(DUBLIN_LAT, DUBLIN_LON, NEW_YORK_LAT, NEW_YORK_LON);
            assertThat(km).isBetween(5050.0, 5150.0);
        }
    }

    @Nested
    class NewDevice {
        private final NewDeviceRule rule = new NewDeviceRule(props);

        @Test
        void largePaymentFromUnknownDeviceFires() {
            var h = history(3, 50, Set.of("device-1"), List.of(), null);
            var t = txn("t1", "cust-1", "1500", "device-X", DUBLIN_LAT, DUBLIN_LON, "RETAIL", T0);
            assertThat(rule.evaluate(t, h)).isPresent();
        }

        @Test
        void firstEverTransactionIsNotPenalised() {
            var t = txn("t1", "cust-1", "1500", "device-X", DUBLIN_LAT, DUBLIN_LON, "RETAIL", T0);
            assertThat(rule.evaluate(t, CustomerSnapshot.empty())).isEmpty();
        }
    }

    @Nested
    class HighRiskMerchant {
        private final HighRiskMerchantRule rule = new HighRiskMerchantRule(props);

        @Test
        void matchesCaseInsensitively() {
            var t = txn("t1", "cust-1", "10", "device-1", DUBLIN_LAT, DUBLIN_LON, "crypto", T0);
            assertThat(rule.evaluate(t, CustomerSnapshot.empty())).isPresent();
        }
    }
}
