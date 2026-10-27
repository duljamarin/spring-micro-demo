package com.demo.review.dto;

/**
 * What the UI needs to tell the truth about where scores come from: which
 * model is configured, whether it is actually reachable right now, and what
 * happens if it isn't. The React app polls this to render its engine banner,
 * which is why the reachability probe lives behind its own endpoint rather
 * than being inferred from a failed submit.
 */
public record ScoringEngineResponse(
        boolean llmEnabled,
        boolean llmReachable,
        String model,
        String baseUrl,
        String fallbackScorer,
        String detail
) {
}
