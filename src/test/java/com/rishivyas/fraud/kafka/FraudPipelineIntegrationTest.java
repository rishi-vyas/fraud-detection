package com.rishivyas.fraud.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rishivyas.fraud.model.Transaction;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import static com.rishivyas.fraud.TestData.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end: transaction in on Kafka -> scored -> decision out on Kafka; poison pills -> DLT.
 * Runs against an in-process broker, so no Docker is needed for {@code mvn verify}.
 */
@SpringBootTest(properties = {
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
        "fraud.topics.partitions=1"
})
@EmbeddedKafka(partitions = 1, kraft = true)
class FraudPipelineIntegrationTest {

    @Autowired
    private EmbeddedKafkaBroker broker;
    @Autowired
    private TransactionProducer producer;

    private final ObjectMapper json = new ObjectMapper();
    private Consumer<String, String> consumer;

    private Consumer<String, String> consumerFor(String topic) {
        Map<String, Object> props = KafkaTestUtils.consumerProps("test-" + UUID.randomUUID(), "true", broker);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        consumer = new DefaultKafkaConsumerFactory<>(props, new StringDeserializer(), new StringDeserializer())
                .createConsumer();
        broker.consumeFromAnEmbeddedTopic(consumer, topic);
        return consumer;
    }

    @AfterEach
    void close() {
        if (consumer != null) {
            consumer.close();
        }
    }

    @Test
    void highRiskTransactionProducesDeclineDecisionAndAlert() throws Exception {
        Consumer<String, String> alerts = consumerFor("fraud-alerts");
        Transaction risky = txn("it-risky-1", "cust-it-1", "9000", "device-1", DUBLIN_LAT, DUBLIN_LON, "GAMBLING", T0);

        producer.send(risky);

        ConsumerRecord<String, String> record = KafkaTestUtils.getSingleRecord(alerts, "fraud-alerts", Duration.ofSeconds(30));
        JsonNode decision = json.readTree(record.value());
        assertThat(record.key()).isEqualTo("cust-it-1");
        assertThat(decision.get("transactionId").asText()).isEqualTo("it-risky-1");
        assertThat(decision.get("decision").asText()).isEqualTo("REVIEW"); // 30 (amount) + 15 (merchant) = 45
    }

    @Test
    void approvedTransactionIsPublishedToDecisions() throws Exception {
        Consumer<String, String> decisions = consumerFor("fraud-decisions");

        producer.send(txn("it-ok-1", "cust-it-2", "25", "device-1", DUBLIN_LAT, DUBLIN_LON, "GROCERY", T0));

        ConsumerRecord<String, String> record = KafkaTestUtils.getSingleRecord(decisions, "fraud-decisions", Duration.ofSeconds(30));
        assertThat(json.readTree(record.value()).get("decision").asText()).isEqualTo("APPROVE");
    }

    @Test
    void malformedPayloadIsRoutedToDeadLetterTopic() {
        Consumer<String, String> dlt = consumerFor("transactions.DLT");
        Map<String, Object> producerProps = KafkaTestUtils.producerProps(broker);
        var factory = new DefaultKafkaProducerFactory<String, String>(producerProps, new StringSerializer(), new StringSerializer());
        try {
            new KafkaTemplate<>(factory).send("transactions", "cust-bad", "{not valid json").join();
        } finally {
            factory.destroy();
        }

        ConsumerRecord<String, String> record = KafkaTestUtils.getSingleRecord(dlt, "transactions.DLT", Duration.ofSeconds(30));
        assertThat(record.value()).isEqualTo("{not valid json");
        assertThat(record.headers().lastHeader("kafka_dlt-exception-message")).isNotNull();
    }
}
