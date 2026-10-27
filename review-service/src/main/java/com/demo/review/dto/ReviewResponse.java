package com.demo.review.dto;

import com.demo.review.model.Review;

import java.time.Instant;
import java.util.List;

public record ReviewResponse(
        Long id,
        Long productId,
        String productName,
        String author,
        String text,
        Instant submittedAt,
        int overallScore,
        int qualityScore,
        int valueScore,
        int reliabilityScore,
        String sentiment,
        String summary,
        List<String> topics,
        String scoreSource,
        String scoredBy,
        long scoringMillis
) {
    public static ReviewResponse from(Review review, String productName) {
        return new ReviewResponse(
                review.getId(),
                review.getProductId(),
                productName,
                review.getAuthor(),
                review.getText(),
                review.getSubmittedAt(),
                review.getOverallScore(),
                review.getQualityScore(),
                review.getValueScore(),
                review.getReliabilityScore(),
                review.getSentiment().name(),
                review.getSummary(),
                review.getTopics(),
                review.getScoreSource().name(),
                review.getScoredBy(),
                review.getScoringMillis()
        );
    }
}
