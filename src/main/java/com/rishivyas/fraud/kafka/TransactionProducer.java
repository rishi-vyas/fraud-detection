package com.rishivyas.fraud.kafka;

import com.rishivyas.fraud.config.FraudProperties;
import com.rishivyas.fraud.model.Transaction;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Publishes transactions keyed by customerId. The key is what guarantees per-customer
 * ordering: all of a customer's events land on one partition and are processed in sequence.
 */
@Component
public class TransactionProducer {

    private final KafkaTemplate<String, Object> template;
    private final String topic;

    public TransactionProducer(KafkaTemplate<String, Object> template, FraudProperties properties) {
        this.template = template;
        this.topic = properties.topics().transactions();
    }

    /** Blocks until the broker acknowledges (acks=all), so a 202 response means the event is durable. */
    public void send(Transaction txn) {
        DecisionPublisher.await(template.send(topic, txn.customerId(), txn), txn.transactionId());
    }

    /** Fire-and-forget variant used by the load simulator. */
    public void sendAsync(Transaction txn) {
        template.send(topic, txn.customerId(), txn);
    }
}
