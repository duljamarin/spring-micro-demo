package com.demo.review.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Review.applyScore is the chokepoint every scorer goes through, so the
 * clamping it does is the only thing standing between a confused model and a
 * poisoned average. Worth pinning.
 */
class ReviewTest {

    private static Review review() {
        return new Review(1L, "tester", "text");
    }

    @Test
    void clampsScoresFromAModelThatIgnoredTheScale() {
        Review review = review();
        review.applyScore(150, -20, 100, 0, Sentiment.POSITIVE, "s", List.of(), ScoreSource.LLM, "m", 1L);

        assertEquals(100, review.getOverallScore());
        assertEquals(0, review.getQualityScore());
        assertEquals(100, review.getValueScore());
        assertEquals(0, review.getReliabilityScore());
    }

    @Test
    void defaultsMissingSentimentAndTopicsInsteadOfStoringNulls() {
        Review review = review();
        review.applyScore(60, 60, 60, 60, null, null, null, ScoreSource.LLM, "m", 1L);

        assertEquals(Sentiment.NEUTRAL, review.getSentiment());
        assertEquals(List.of(), review.getTopics());
    }

    @Test
    void truncatesAnOverlongSummaryToWhatTheColumnHolds() {
        Review review = review();
        review.applyScore(60, 60, 60, 60, Sentiment.NEUTRAL, "x".repeat(900), List.of(),
                ScoreSource.LLM, "m", 1L);

        assertEquals(500, review.getSummary().length());
    }

    @Test
    void sentimentParsingToleratesWhatSmallModelsActuallyReturn() {
        assertEquals(Sentiment.POSITIVE, Sentiment.parse("Positive"));
        assertEquals(Sentiment.POSITIVE, Sentiment.parse("  very positive  "));
        assertEquals(Sentiment.NEGATIVE, Sentiment.parse("NEGATIVE"));
        assertEquals(Sentiment.NEUTRAL, Sentiment.parse("mixed"));
        assertEquals(Sentiment.NEUTRAL, Sentiment.parse(null));
    }
}
