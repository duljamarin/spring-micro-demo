package com.demo.review.scoring;

import com.demo.review.model.ScoreSource;

/**
 * A score plus the provenance of it: which scorer answered, under what name,
 * and how long it took. Carried as one value so that nothing downstream can
 * store a score while forgetting to record that it came from the fallback.
 */
public record ScoringOutcome(
        ReviewScore score,
        ScoreSource source,
        String scoredBy,
        long millis
) {
}
