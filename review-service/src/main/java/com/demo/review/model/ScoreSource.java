package com.demo.review.model;

/**
 * Which scorer actually produced the numbers on a review. Persisted and
 * exposed over the API on purpose: a score produced by the keyword fallback
 * is not the same evidence as one produced by the model, and the UI has to
 * be able to say so rather than quietly presenting both as equal.
 */
public enum ScoreSource {
    /** Scored by the local LLM through Spring AI. */
    LLM,
    /** Scored by the deterministic fallback because the model was unreachable. */
    FALLBACK
}
