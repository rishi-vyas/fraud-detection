package com.rishivyas.fraud.service;

import com.rishivyas.fraud.config.FraudProperties;
import com.rishivyas.fraud.model.Decision;
import com.rishivyas.fraud.model.Evaluation;
import com.rishivyas.fraud.model.FraudDecision;
import com.rishivyas.fraud.model.RuleResult;
import com.rishivyas.fraud.model.Transaction;
import com.rishivyas.fraud.rules.FraudRule;
import com.rishivyas.fraud.state.CustomerProfile;
import com.rishivyas.fraud.state.CustomerProfileStore;
import com.rishivyas.fraud.state.CustomerSnapshot;
import com.rishivyas.fraud.state.DecisionStore;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Runs every {@link FraudRule} against a transaction, sums the risk points, and maps the
 * score to APPROVE / REVIEW / DECLINE. Evaluation and the state update happen under the
 * customer's lock, so the read-score-update sequence is atomic per customer.
 */
@Service
public class FraudScoringService {

    private static final int MAX_SCORE = 100;

    private final List<FraudRule> rules;
    private final FraudProperties properties;
    private final CustomerProfileStore profiles;
    private final DecisionStore decisions;
    private final MeterRegistry meters;
    private final Timer scoringTimer;
    private final Counter duplicates;

    public FraudScoringService(List<FraudRule> rules, FraudProperties properties, CustomerProfileStore profiles,
                               DecisionStore decisions, MeterRegistry meters) {
        this.rules = List.copyOf(rules);
        this.properties = properties;
        this.profiles = profiles;
        this.decisions = decisions;
        this.meters = meters;
        this.scoringTimer = Timer.builder("fraud.scoring.latency")
                .description("Time to evaluate one transaction")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(meters);
        this.duplicates = Counter.builder("fraud.duplicates")
                .description("Redelivered transactions skipped by idempotency check")
                .register(meters);
    }

    public Evaluation evaluate(Transaction txn) {
        long start = System.nanoTime();
        CustomerProfile profile = profiles.profileFor(txn.customerId());

        synchronized (profile) {
            Optional<FraudDecision> existing = decisions.find(txn.transactionId());
            if (existing.isPresent()) {
                duplicates.increment();
                return new Evaluation(existing.get(), true);
            }

            CustomerSnapshot history = profile.snapshot();
            List<RuleResult> triggered = rules.stream()
                    .map(rule -> rule.evaluate(txn, history))
                    .flatMap(Optional::stream)
                    .toList();
            int score = Math.min(MAX_SCORE, triggered.stream().mapToInt(RuleResult::score).sum());
            Decision decision = properties.decide(score);

            if (decision == Decision.DECLINE) {
                profile.recordAttempt(txn);
            } else {
                profile.recordAccepted(txn);
            }

            long elapsedNanos = System.nanoTime() - start;
            FraudDecision result = new FraudDecision(txn.transactionId(), txn.customerId(), txn.amount(),
                    txn.currency(), decision, score, triggered, Instant.now(), elapsedNanos / 1_000);
            decisions.put(result);
            record(result, elapsedNanos);
            return new Evaluation(result, false);
        }
    }

    private void record(FraudDecision result, long elapsedNanos) {
        scoringTimer.record(elapsedNanos, java.util.concurrent.TimeUnit.NANOSECONDS);
        meters.counter("fraud.decisions", "decision", result.decision().name()).increment();
        result.triggeredRules().forEach(r -> meters.counter("fraud.rules.triggered", "rule", r.rule()).increment());
    }
}
