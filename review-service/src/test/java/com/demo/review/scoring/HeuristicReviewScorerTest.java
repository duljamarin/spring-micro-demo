package com.demo.review.scoring;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The fallback scorer is the one scorer that can be tested without a model,
 * which is most of the reason it is worth having: these assertions pin the
 * behaviour the whole stack falls back on when ollama isn't there.
 */
class HeuristicReviewScorerTest {

    private final HeuristicReviewScorer scorer = new HeuristicReviewScorer();

    @Test
    void scoresPraiseAbovePlainTextAndComplaintsBelowIt() {
        int praise = scorer.score("Excellent build, love it, would recommend to anyone.").overallScore();
        int neutral = scorer.score("It is a keyboard and it arrived on Tuesday.").overallScore();
        int complaint = scorer.score("Broke in a week, terrible, flimsy and overpriced.").overallScore();

        assertEquals(50, neutral, "text with no sentiment words must land exactly on neutral");
        assertTrue(praise > neutral, "praise should score above neutral, got " + praise);
        assertTrue(complaint < neutral, "a complaint should score below neutral, got " + complaint);
    }

    @Test
    void staysInRangeOnPileOnInput() {
        ReviewScore extreme = scorer.score("great great great excellent amazing perfect love solid");
        assertTrue(extreme.overallScore() <= 100, "score must be clamped, got " + extreme.overallScore());

        ReviewScore awful = scorer.score("broke broken terrible awful hate useless waste refund");
        assertTrue(awful.overallScore() >= 0, "score must be clamped, got " + awful.overallScore());
    }

    @Test
    void leavesUnmentionedAspectsAtNeutralInsteadOfCopyingTheOverallScore() {
        // Talks about price only. Quality and reliability were never mentioned,
        // so they must read as "no signal" rather than inheriting the praise.
        ReviewScore score = scorer.score("Excellent value, a real bargain for the money.");

        assertTrue(score.valueScore() > 50, "value was praised, got " + score.valueScore());
        assertEquals(50, score.qualityScore(), "quality was never mentioned");
        assertEquals(50, score.reliabilityScore(), "reliability was never mentioned");
    }

    @Test
    void alwaysProducesAtLeastOneTopicAndAUsableSentimentLabel() {
        ReviewScore score = scorer.score("");

        assertEquals(1, score.topics().size());
        assertEquals("general", score.topics().getFirst());
        assertEquals("NEUTRAL", score.sentiment());
    }
}
