package com.demo.review.model;

/**
 * Coarse sentiment label the model assigns alongside the numeric scores.
 * Deliberately only three values: a local 3B-parameter model is reliable at
 * this granularity and noisy at anything finer.
 */
public enum Sentiment {
    POSITIVE,
    NEUTRAL,
    NEGATIVE;

    /**
     * Lenient parse for whatever the LLM actually produced. Even with a JSON
     * schema in the prompt, small models return "Positive", "very positive"
     * or "mixed" often enough that mapping this strictly would turn a usable
     * score into a hard failure.
     */
    public static Sentiment parse(String raw) {
        if (raw == null) {
            return NEUTRAL;
        }
        String normalized = raw.trim().toUpperCase();
        if (normalized.contains("POSITIVE") && !normalized.startsWith("NON")) {
            return POSITIVE;
        }
        if (normalized.contains("NEGATIVE")) {
            return NEGATIVE;
        }
        return NEUTRAL;
    }
}
