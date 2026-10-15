package com.rishivyas.fraud.api;

import com.rishivyas.fraud.kafka.TransactionProducer;
import com.rishivyas.fraud.model.FraudDecision;
import com.rishivyas.fraud.model.Transaction;
import com.rishivyas.fraud.service.FraudScoringService;
import com.rishivyas.fraud.state.CustomerProfileStore;
import com.rishivyas.fraud.state.DecisionStore;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@Validated
public class FraudController {

    private final TransactionProducer producer;
    private final FraudScoringService scoring;
    private final DecisionStore decisions;
    private final CustomerProfileStore profiles;

    public FraudController(TransactionProducer producer, FraudScoringService scoring,
                           DecisionStore decisions, CustomerProfileStore profiles) {
        this.producer = producer;
        this.scoring = scoring;
        this.decisions = decisions;
        this.profiles = profiles;
    }

    /** Asynchronous path: enqueue on Kafka; the decision appears on fraud-decisions and via GET. */
    @PostMapping("/transactions")
    public ResponseEntity<Map<String, String>> submit(@Valid @RequestBody Transaction txn) {
        producer.send(txn);
        return ResponseEntity.accepted().body(Map.of(
                "transactionId", txn.transactionId(),
                "status", "QUEUED",
                "decisionUrl", "/api/decisions/" + txn.transactionId()));
    }

    /** Synchronous path for inline authorisation checks (and demos): score and return immediately. */
    @PostMapping("/transactions/score")
    public FraudDecision score(@Valid @RequestBody Transaction txn) {
        return scoring.evaluate(txn).decision();
    }

    @GetMapping("/decisions/{transactionId}")
    public ResponseEntity<FraudDecision> decision(@PathVariable String transactionId) {
        return ResponseEntity.of(decisions.find(transactionId));
    }

    @GetMapping("/decisions")
    public List<FraudDecision> recent(@RequestParam(defaultValue = "20") @Min(1) @Max(500) int limit) {
        return decisions.recent(limit);
    }

    @GetMapping("/stats")
    public Map<String, Integer> stats() {
        return Map.of("customersTracked", profiles.size(), "decisionsCached", decisions.size());
    }
}
