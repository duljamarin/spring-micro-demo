package com.demo.review;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Bound from the "scoring.*" keys served by the config server (see
 * review-service.yml in the config repo), so the LLM can be switched off, or
 * the input bound changed, without a rebuild - and reloaded at runtime via
 * /actuator/refresh, same as inventory.low-stock-threshold in product-service.
 */
@ConfigurationProperties(prefix = "scoring")
public class ScoringProperties {

    /**
     * Master switch for the model. Off means every review is scored by the
     * deterministic fallback - useful on a laptop with no ollama installed,
     * and the one way to get fast, repeatable scores in a demo.
     */
    private boolean llmEnabled = true;

    /**
     * Characters of review text sent to the model. A bound is needed because
     * prompt length drives generation time linearly on CPU inference, and an
     * unbounded paste would blow straight through the HTTP read timeout.
     */
    private int maxReviewLength = 1500;

    public boolean isLlmEnabled() {
        return llmEnabled;
    }

    public void setLlmEnabled(boolean llmEnabled) {
        this.llmEnabled = llmEnabled;
    }

    public int getMaxReviewLength() {
        return maxReviewLength;
    }

    public void setMaxReviewLength(int maxReviewLength) {
        this.maxReviewLength = maxReviewLength;
    }
}
