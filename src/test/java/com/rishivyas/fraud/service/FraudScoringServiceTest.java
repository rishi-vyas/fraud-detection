package com.rishivyas.fraud.service;

import com.rishivyas.fraud.config.FraudProperties;
import com.rishivyas.fraud.model.Decision;
import com.rishivyas.fraud.model.Evaluation;
import com.rishivyas.fraud.model.RuleResult;
import com.rishivyas.fraud.rules.AmountSpikeRule;
import com.rishivyas.fraud.rules.HighAmountRule;
import com.rishivyas.fraud.rules.HighRiskMerchantRule;
import com.rishivyas.fraud.rules.ImpossibleTravelRule;
import com.rishivyas.fraud.rules.NewDeviceRule;
import com.rishivyas.fraud.rules.VelocityRule;
import com.rishivyas.fraud.state.CustomerProfileStore;
import com.rishivyas.fraud.state.DecisionStore;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.rishivyas.fraud.TestData.*;
import static org.assertj.core.api.Assertions.assertThat;

class FraudScoringServiceTest {

    private FraudScoringService service;
    private CustomerProfileStore profiles;
    private SimpleMeterRegistry meters;

    @BeforeEach
    void setUp() {
        FraudProperties props = properties();
        profiles = new CustomerProfileStore();
        meters = new SimpleMeterRegistry();
        service = new FraudScoringService(
                List.of(new HighAmountRule(props), new AmountSpikeRule(props), new VelocityRule(props),
                        new ImpossibleTravelRule(props), new NewDeviceRule(props), new HighRiskMerchantRule(props)),
                props, profiles, new DecisionStore(1000), meters);
    }

    /** Six ordinary purchases an hour apart establish a baseline (mean ~€40, device-1, Dublin). */
    private void establishBaseline() {
        for (int i = 0; i < 6; i++) {
            service.evaluate(txn("base-" + i, "40", T0.plusSeconds(i * 3600L)));
        }
    }

    @Test
    void ordinaryPurchaseIsApproved() {
        establishBaseline();
        Evaluation e = service.evaluate(txn("t1", "35", T0.plusSeconds(7 * 3600)));
        assertThat(e.decision().decision()).isEqualTo(Decision.APPROVE);
        assertThat(e.decision().riskScore()).isZero();
    }

    @Test
    void clonedCardAbroadIsDeclined() {
        establishBaseline();
        var abroad = txn("t1", "cust-1", "2500", "device-X", NEW_YORK_LAT, NEW_YORK_LON, "CRYPTO",
                T0.plusSeconds(5 * 3600 + 600));

        var d = service.evaluate(abroad).decision();

        assertThat(d.decision()).isEqualTo(Decision.DECLINE);
        assertThat(d.riskScore()).isEqualTo(100); // capped
        assertThat(d.triggeredRules()).extracting(RuleResult::rule)
                .contains("IMPOSSIBLE_TRAVEL", "NEW_DEVICE", "AMOUNT_SPIKE", "HIGH_RISK_MERCHANT");
    }

    @Test
    void cardTestingBurstEscalatesToReview() {
        establishBaseline();
        var start = T0.plusSeconds(10 * 3600);
        Decision last = null;
        for (int i = 0; i < 7; i++) {
            last = service.evaluate(txn("burst-" + i, "1.50", start.plusSeconds(i * 3L))).decision().decision();
        }
        assertThat(last).isEqualTo(Decision.REVIEW);
    }

    @Test
    void redeliveredTransactionIsNotDoubleCounted() {
        var t = txn("dup-1", "40", T0);
        Evaluation first = service.evaluate(t);
        Evaluation second = service.evaluate(t);

        assertThat(first.duplicate()).isFalse();
        assertThat(second.duplicate()).isTrue();
        assertThat(second.decision()).isEqualTo(first.decision());
        assertThat(profiles.profileFor("cust-1").snapshot().acceptedCount()).isEqualTo(1);
        assertThat(meters.counter("fraud.duplicates").count()).isEqualTo(1.0);
    }

    @Test
    void declinedTransactionDoesNotPoisonTheBaseline() {
        establishBaseline();
        service.evaluate(txn("bad", "cust-1", "9000", "device-X", NEW_YORK_LAT, NEW_YORK_LON, "GAMBLING",
                T0.plusSeconds(5 * 3600 + 60)));

        var snapshot = profiles.profileFor("cust-1").snapshot();
        assertThat(snapshot.knownDevices()).containsExactly("device-1");
        assertThat(snapshot.meanAmount()).isEqualTo(40.0);
        assertThat(snapshot.lastAccepted().latitude()).isEqualTo(DUBLIN_LAT);
    }

    @Test
    void customersAreIsolated() {
        establishBaseline();
        var other = txn("o1", "cust-2", "2500", "device-Z", NEW_YORK_LAT, NEW_YORK_LON, "RETAIL", T0.plusSeconds(60));
        assertThat(service.evaluate(other).decision().triggeredRules()).extracting(RuleResult::rule)
                .doesNotContain("IMPOSSIBLE_TRAVEL", "NEW_DEVICE");
    }

    @Test
    void decisionCountersAreRecorded() {
        establishBaseline();
        assertThat(meters.counter("fraud.decisions", "decision", "APPROVE").count()).isEqualTo(6.0);
    }
}
