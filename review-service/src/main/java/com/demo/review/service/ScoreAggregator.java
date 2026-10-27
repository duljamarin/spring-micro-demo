package com.demo.review.service;

import com.demo.review.dto.ProductScoreResponse;
import com.demo.review.model.Review;
import com.demo.review.model.ScoreSource;
import com.demo.review.model.Sentiment;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.ToIntFunction;

/**
 * Rolls per-review scores up into one row per product.
 *
 * Pure function of its inputs, no repository and no Feign client, so the
 * arithmetic that the whole scoreboard rests on is unit-testable without a
 * running stack.
 */
@Component
public class ScoreAggregator {

    /** How many distinct topics a product row advertises. */
    private static final int TOP_TOPICS = 4;

    public ProductScoreResponse aggregate(Long productId, String productName, List<Review> reviews) {
        if (reviews.isEmpty()) {
            return ProductScoreResponse.unscored(productId, productName);
        }

        return new ProductScoreResponse(
                productId,
                productName,
                reviews.size(),
                reviews.stream().filter(r -> r.getScoreSource() == ScoreSource.LLM).count(),
                mean(reviews, Review::getOverallScore),
                mean(reviews, Review::getQualityScore),
                mean(reviews, Review::getValueScore),
                mean(reviews, Review::getReliabilityScore),
                countOf(reviews, Sentiment.POSITIVE),
                countOf(reviews, Sentiment.NEUTRAL),
                countOf(reviews, Sentiment.NEGATIVE),
                topTopics(reviews),
                lastReviewedAt(reviews));
    }

    /**
     * Plain unweighted mean, rounded half-up. Deliberately not a
     * recency-weighted or confidence-weighted average: with a handful of
     * reviews per product any weighting scheme says more about the scheme
     * than about the product, and an average nobody can reproduce by hand is
     * a bad default for a reference project.
     */
    private static int mean(List<Review> reviews, ToIntFunction<Review> field) {
        int sum = reviews.stream().mapToInt(field).sum();
        return (int) Math.round((double) sum / reviews.size());
    }

    private static long countOf(List<Review> reviews, Sentiment sentiment) {
        return reviews.stream().filter(r -> r.getSentiment() == sentiment).count();
    }

    /**
     * Topics ordered by how many reviews mention them, so a product's row
     * leads with what customers actually keep bringing up. Ties keep
     * insertion order, which makes the output stable across calls.
     */
    private static List<String> topTopics(List<Review> reviews) {
        Map<String, Long> counts = new LinkedHashMap<>();
        reviews.stream()
                .flatMap(r -> r.getTopics().stream())
                .filter(topic -> topic != null && !topic.isBlank())
                .map(topic -> topic.trim().toLowerCase())
                .forEach(topic -> counts.merge(topic, 1L, Long::sum));

        return counts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(TOP_TOPICS)
                .map(Map.Entry::getKey)
                .toList();
    }

    private static Instant lastReviewedAt(List<Review> reviews) {
        return reviews.stream()
                .map(Review::getSubmittedAt)
                .max(Comparator.naturalOrder())
                .orElse(null);
    }
}
