package com.rishivyas.fraud.kafka;

import com.rishivyas.fraud.config.FraudProperties;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

import java.util.LinkedHashMap;
import java.util.Map;

@Configuration
public class KafkaConfig {

    private final FraudProperties.Topics topics;

    public KafkaConfig(FraudProperties properties) {
        this.topics = properties.topics();
    }

    @Bean
    NewTopic transactionsTopic() {
        return TopicBuilder.name(topics.transactions()).partitions(topics.partitions()).replicas(1).build();
    }

    @Bean
    NewTopic decisionsTopic() {
        return TopicBuilder.name(topics.decisions()).partitions(topics.partitions()).replicas(1).build();
    }

    @Bean
    NewTopic alertsTopic() {
        return TopicBuilder.name(topics.alerts()).partitions(topics.partitions()).replicas(1).build();
    }

    /** DLT needs at least as many partitions as the source, since the recoverer keeps the partition number. */
    @Bean
    NewTopic deadLetterTopic() {
        return TopicBuilder.name(topics.deadLetter()).partitions(topics.partitions()).replicas(1).build();
    }

    /**
     * Transient failures are retried twice, 500 ms apart; then the record goes to
     * {@code transactions.DLT} and the partition keeps moving. Invalid payloads skip the retries.
     *
     * <p>Records that failed JSON deserialisation are still raw bytes, so they are published
     * through a byte-array template; everything else goes through the JSON template.
     */
    @Bean
    CommonErrorHandler kafkaErrorHandler(KafkaTemplate<String, Object> jsonTemplate, KafkaProperties kafkaProperties) {
        Map<String, Object> byteConfig = kafkaProperties.buildProducerProperties(null);
        byteConfig.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class);
        KafkaTemplate<String, byte[]> bytesTemplate = new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(byteConfig));

        Map<Class<?>, KafkaOperations<?, ?>> templates = new LinkedHashMap<>();
        templates.put(byte[].class, bytesTemplate);
        templates.put(Object.class, jsonTemplate);

        DefaultErrorHandler handler = new DefaultErrorHandler(
                new DeadLetterPublishingRecoverer(templates), new FixedBackOff(500L, 2));
        handler.addNotRetryableExceptions(InvalidTransactionException.class);
        return handler;
    }
}
