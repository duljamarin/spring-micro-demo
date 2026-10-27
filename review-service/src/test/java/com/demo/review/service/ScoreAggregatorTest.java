package com.demo.review.service;

import com.demo.review.dto.ProductScoreResponse;
import com.demo.review.model.Review;
import com.demo.review.model.ScoreSource;
import com.demo.review.model.Sentiment;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScoreAggregatorTest {

    private final ScoreAggregator aggregator = new ScoreAggregator();

    private static Review scored(int overall, Sentiment sentiment, ScoreSource source, List<String> topics) {
        Review review = new Review(1L, "tester", "some review text that is long enough");
        review.applyScore(overall, overall, overall, overall, sentiment, "summary", topics, source, "test", 0L);
        return review;
    }

    @Test
    void emptyProductYieldsAnExplicitlyUnscoredRowRatherThanBeingOmitted() {
        ProductScoreResponse row = aggregator.aggregate(7L, "USB-C Dock", List.of());

        assertEquals(0, row.reviewCount());
        assertEquals(0, row.overallScore());
        assertEquals("USB-C Dock", row.productName());
        assertTrue(row.topTopics().isEmpty());
        assertNull(row.lastReviewedAt(), "a product with no reviews has no last-reviewed timestamp");
    }

    @Test
    void averagesAreRoundedHalfUp() {
        // 70 and 75 average to 72.5, which must present as 73, not 72.
        ProductScoreResponse row = aggregator.aggregate(1L, "Keyboard", List.of(
                scored(70, Sentiment.POSITIVE, ScoreSource.LLM, List.of("quality")),
                scored(75, Sentiment.POSITIVE, ScoreSource.LLM, List.of("quality"))));

        assertEquals(73, row.overallScore());
    }

    @Test
    void countsSentimentsAndReportsHowManyScoresCameFromTheModel() {
        ProductScoreResponse row = aggregator.aggregate(1L, "Keyboard", List.of(
                scored(90, Sentiment.POSITIVE, ScoreSource.LLM, List.of("quality")),
                scored(50, Sentiment.NEUTRAL, ScoreSource.FALLBACK, List.of("value")),
                scored(10, Sentiment.NEGATIVE, ScoreSource.FALLBACK, List.of("value"))));

        assertEquals(3, row.reviewCount());
        assertEquals(1, row.llmScoredCount(), "only one of the three was scored by the model");
        assertEquals(1, row.positiveCount());
        assertEquals(1, row.neutralCount());
        assertEquals(1, row.negativeCount());
    }

    @Test
    void topTopicsAreOrderedByHowManyReviewsMentionThem() {
        ProductScoreResponse row = aggregator.aggregate(1L, "Keyboard", List.of(
                scored(80, Sentiment.POSITIVE, ScoreSource.LLM, List.of("shipping")),
                scored(80, Sentiment.POSITIVE, ScoreSource.LLM, List.of("key feel", "shipping")),
                scored(80, Sentiment.POSITIVE, ScoreSource.LLM, List.of("key feel", "shipping"))));

        assertEquals(List.of("shipping", "key feel"), row.topTopics());
    }

    @Test
    void topicCasingAndWhitespaceFromTheModelAreNormalisedBeforeCounting() {
        // A model will happily return "Battery Life", "battery life" and
        // " battery life" across three calls; they are one topic.
        ProductScoreResponse row = aggregator.aggregate(1L, "Keyboard", List.of(
                scored(80, Sentiment.POSITIVE, ScoreSource.LLM, List.of("Battery Life")),
                scored(80, Sentiment.POSITIVE, ScoreSource.LLM, List.of(" battery life ")),
                scored(80, Sentiment.POSITIVE, ScoreSource.LLM, List.of("battery life"))));

        assertEquals(List.of("battery life"), row.topTopics());
    }
}
