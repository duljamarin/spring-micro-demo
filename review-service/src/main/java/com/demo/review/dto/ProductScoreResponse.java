package com.demo.review.dto;

import java.time.Instant;
import java.util.List;

/**
 * One row of the scoreboard: a product's aggregated scores across every
 * review of it.
 *
 * {@code llmScoredCount} is part of the payload rather than an afterthought
 * because it is the honest caveat on every other number here - an aggregate
 * built from 1 model score and 9 fallback scores should not be presented the
 * same way as one built from 10 model scores, and only the API knows the mix.
 */
public record ProductScoreResponse(
        Long productId,
        String productName,
        long reviewCount,
        long llmScoredCount,
        int overallScore,
        int qualityScore,
        int valueScore,
        int reliabilityScore,
        long positiveCount,
        long neutralCount,
        long negativeCount,
        List<String> topTopics,
        Instant lastReviewedAt
) {
    /** A product that exists in the catalog but has no reviews yet. */
    public static ProductScoreResponse unscored(Long productId, String productName) {
        return new ProductScoreResponse(productId, productName, 0, 0,
                0, 0, 0, 0, 0, 0, 0, List.of(), null);
    }
}
