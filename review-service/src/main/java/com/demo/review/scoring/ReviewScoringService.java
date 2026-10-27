package com.demo.review.scoring;

import com.demo.review.ScoringProperties;
import com.demo.review.model.ScoreSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Picks a scorer and records which one answered.
 *
 * The LLM is tried first; anything that goes wrong - ollama not running, the
 * model not pulled, the HTTP read timing out, the reply not binding to
 * {@link ReviewScore}, or the circuit breaker being open from earlier
 * failures - lands in the same place: the deterministic scorer, with the
 * result tagged {@code FALLBACK}.
 *
 * Catching {@code Exception} this broadly is normally a smell. It is correct
 * here because there is exactly one sensible reaction to every possible
 * failure of a best-effort enrichment step, and because the alternative -
 * rejecting the customer's review because a local model was busy - is worse
 * than scoring it crudely and saying so.
 */
@Service
public class ReviewScoringService {

    private static final Logger log = LoggerFactory.getLogger(ReviewScoringService.class);

    private final LlmReviewScorer llmScorer;
    private final HeuristicReviewScorer heuristicScorer;
    private final ScoringProperties properties;

    public ReviewScoringService(LlmReviewScorer llmScorer,
                                HeuristicReviewScorer heuristicScorer,
                                ScoringProperties properties) {
        this.llmScorer = llmScorer;
        this.heuristicScorer = heuristicScorer;
        this.properties = properties;
    }

    public ScoringOutcome score(String productName, String reviewText) {
        long start = System.nanoTime();

        if (properties.isLlmEnabled()) {
            try {
                ReviewScore score = llmScorer.score(productName, reviewText);
                if (score != null) {
                    return new ScoringOutcome(score, ScoreSource.LLM, llmScorer.modelId(), elapsedMillis(start));
                }
                log.warn("LLM returned no parseable score for a review of '{}'; using fallback", productName);
            } catch (Exception ex) {
                // Message only, no stack trace: with ollama simply not running
                // this fires on every submit and a stack trace per review
                // would bury everything else in the log.
                log.warn("LLM scoring failed ({}: {}); using fallback scorer",
                        ex.getClass().getSimpleName(), ex.getMessage());
            }
        }

        ReviewScore score = heuristicScorer.score(reviewText);
        return new ScoringOutcome(score, ScoreSource.FALLBACK, "heuristic", elapsedMillis(start));
    }

    private static long elapsedMillis(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }
}
