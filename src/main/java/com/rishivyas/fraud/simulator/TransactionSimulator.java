package com.rishivyas.fraud.simulator;

import com.rishivyas.fraud.kafka.TransactionProducer;
import com.rishivyas.fraud.model.Transaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.IntStream;

/**
 * Generates a realistic stream of mostly-legitimate traffic with injected fraud patterns
 * (bursts, impossible travel, account takeover). Enable with {@code fraud.simulator.enabled=true}.
 */
@Component
@ConditionalOnProperty(name = "fraud.simulator.enabled", havingValue = "true")
public class TransactionSimulator {

    private static final Logger log = LoggerFactory.getLogger(TransactionSimulator.class);

    private record City(String name, double lat, double lon) {
    }

    private record Customer(String id, City home, String device) {
    }

    private static final List<City> CITIES = List.of(
            new City("Dublin", 53.3498, -6.2603), new City("Cork", 51.8985, -8.4756),
            new City("London", 51.5072, -0.1276), new City("Paris", 48.8566, 2.3522),
            new City("New York", 40.7128, -74.0060), new City("Singapore", 1.3521, 103.8198),
            new City("Lagos", 6.5244, 3.3792), new City("Sao Paulo", -23.5505, -46.6333));

    private static final List<String> SAFE_CATEGORIES = List.of("GROCERY", "RESTAURANT", "TRANSPORT", "RETAIL", "PHARMACY");
    private static final List<String> RISKY_CATEGORIES = List.of("GAMBLING", "CRYPTO", "GIFT_CARDS");

    private final TransactionProducer producer;
    private final List<Customer> customers;

    public TransactionSimulator(TransactionProducer producer) {
        this.producer = producer;
        this.customers = IntStream.range(0, 50)
                .mapToObj(i -> new Customer("cust-%03d".formatted(i), CITIES.get(i % 2), "device-%03d".formatted(i)))
                .toList();
        log.info("Transaction simulator enabled for {} customers", customers.size());
    }

    @Scheduled(fixedRateString = "${fraud.simulator.interval-ms:200}")
    void emit() {
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        Customer c = customers.get(rnd.nextInt(customers.size()));
        double roll = rnd.nextDouble();

        if (roll < 0.01) {
            // Card-testing burst: many small charges in seconds.
            for (int i = 0; i < 8; i++) {
                producer.sendAsync(txn(c, c.home(), c.device(), amount(1, 5), "RETAIL"));
            }
        } else if (roll < 0.03) {
            // Cloned card used on another continent minutes after a home purchase.
            City far = CITIES.get(4 + rnd.nextInt(4));
            producer.sendAsync(txn(c, far, "device-" + UUID.randomUUID().toString().substring(0, 8),
                    amount(800, 3000), RISKY_CATEGORIES.get(rnd.nextInt(RISKY_CATEGORIES.size()))));
        } else if (roll < 0.05) {
            // Account takeover: large purchase from a new device at home.
            producer.sendAsync(txn(c, c.home(), "device-new-" + rnd.nextInt(1000), amount(2000, 9000), "RETAIL"));
        } else {
            producer.sendAsync(txn(c, c.home(), c.device(), amount(5, 150),
                    SAFE_CATEGORIES.get(rnd.nextInt(SAFE_CATEGORIES.size()))));
        }
    }

    private static Transaction txn(Customer c, City city, String device, BigDecimal amount, String category) {
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        return new Transaction(UUID.randomUUID().toString(), c.id(), amount, "EUR",
                "merchant-" + rnd.nextInt(500), category, device,
                city.lat() + rnd.nextDouble(-0.05, 0.05), city.lon() + rnd.nextDouble(-0.05, 0.05), Instant.now());
    }

    private static BigDecimal amount(double min, double max) {
        return BigDecimal.valueOf(ThreadLocalRandom.current().nextDouble(min, max)).setScale(2, RoundingMode.HALF_UP);
    }
}
