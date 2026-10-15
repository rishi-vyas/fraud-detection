package com.rishivyas.fraud.kafka;

import com.rishivyas.fraud.model.Evaluation;
import com.rishivyas.fraud.model.Transaction;
import com.rishivyas.fraud.service.FraudScoringService;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.stream.Collectors;

@Component
public class TransactionConsumer {

    private static final Logger log = LoggerFactory.getLogger(TransactionConsumer.class);

    private final FraudScoringService scoring;
    private final DecisionPublisher publisher;
    private final Validator validator;

    public TransactionConsumer(FraudScoringService scoring, DecisionPublisher publisher, Validator validator) {
        this.scoring = scoring;
        this.publisher = publisher;
        this.validator = validator;
    }

    @KafkaListener(topics = "${fraud.topics.transactions}", groupId = "${spring.kafka.consumer.group-id}")
    public void onTransaction(ConsumerRecord<String, Transaction> record) {
        Transaction txn = record.value();
        validate(txn, record);

        Evaluation evaluation = scoring.evaluate(txn);
        if (evaluation.duplicate()) {
            log.info("Skipping redelivered transaction {}", txn.transactionId());
            return;
        }
        // Publish synchronously: the offset is only committed after the decision is durably written.
        publisher.publish(evaluation.decision());
        log.debug("{} -> {} (score {})", txn.transactionId(),
                evaluation.decision().decision(), evaluation.decision().riskScore());
    }

    private void validate(Transaction txn, ConsumerRecord<String, Transaction> record) {
        if (txn == null) {
            throw new InvalidTransactionException("Empty payload at offset " + record.offset());
        }
        Set<ConstraintViolation<Transaction>> violations = validator.validate(txn);
        if (!violations.isEmpty()) {
            String detail = violations.stream()
                    .map(v -> v.getPropertyPath() + " " + v.getMessage())
                    .sorted()
                    .collect(Collectors.joining(", "));
            throw new InvalidTransactionException("Invalid transaction " + txn.transactionId() + ": " + detail);
        }
    }
}
