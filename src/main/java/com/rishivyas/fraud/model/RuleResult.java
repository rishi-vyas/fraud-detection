package com.rishivyas.fraud.model;

/** A single rule that fired, with the risk points it contributed and a human-readable reason. */
public record RuleResult(String rule, int score, String reason) {
}
