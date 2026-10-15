package com.rishivyas.fraud.kafka;

import com.rishivyas.fraud.config.FraudProperties;
import com.rishivyas.fraud.model.Decision;
import com.rishivyas.fraud.model.FraudDecision;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** Writes every decision to {@code fraud-decisions}, and non-approvals to {@code fraud-alerts} too. */
@Component
public class DecisionPublisher {

    private static final long SEND_TIMEOUT_SECONDS = 10;

    private final KafkaTemplate<String, Object> template;
    private final FraudProperties.Topics topics;

    public DecisionPublisher(KafkaTemplate<String, Object> template, FraudProperties properties) {
        this.template = template;
        this.topics = properties.topics();
    }

    public void publish(FraudDecision decision) {
        CompletableFuture<?> toDecisions = template.send(topics.decisions(), decision.customerId(), decision);
        CompletableFuture<?> toAlerts = decision.decision() == Decision.APPROVE
                ? CompletableFuture.completedFuture(null)
                : template.send(topics.alerts(), decision.customerId(), decision);
        await(CompletableFuture.allOf(toDecisions, toAlerts), decision.transactionId());
    }

    static void await(CompletableFuture<?> future, String transactionId) {
        try {
            future.get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while publishing " + transactionId, e);
        } catch (ExecutionException | TimeoutException e) {
            throw new IllegalStateException("Failed to publish " + transactionId, e);
        }
    }
}
