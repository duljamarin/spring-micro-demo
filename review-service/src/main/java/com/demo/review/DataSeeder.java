package com.demo.review;

import com.demo.review.model.ScoreSource;
import com.demo.review.model.Review;
import com.demo.review.model.Sentiment;
import com.demo.review.repository.ReviewRepository;
import com.demo.review.scoring.HeuristicReviewScorer;
import com.demo.review.scoring.ReviewScore;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Seeds a few reviews so the scoreboard isn't empty on first load.
 *
 * Seeded with the *keyword* scorer, never the LLM, for two reasons. One,
 * startup must not block for 20 seconds on CPU inference, and must work with
 * no ollama installed at all. Two, it makes the LLM integration
 * demonstrable: these rows arrive tagged FALLBACK with aspect scores stuck at
 * 50 and a boilerplate summary, and hitting "Re-score" in the UI replaces
 * them with real model output on the same text. The difference is the demo.
 *
 * Product ids match product-service's own seeded catalog (1 keyboard,
 * 2 monitor, 3 dock). Product 3 is left unreviewed on purpose so the
 * scoreboard has an empty row to render.
 */
@Component
public class DataSeeder implements CommandLineRunner {

    private record Seed(Long productId, String author, String text) {
    }

    private static final List<Seed> SEEDS = List.of(
            new Seed(1L, "dana",
                    "Build quality is excellent and the switches feel great, though it was a bit "
                            + "expensive for what you get. Three weeks in and nothing has broken."),
            new Seed(1L, "sam",
                    "Arrived with two keys rattling and the plastic case feels flimsy. "
                            + "Disappointed at this price - I want a refund."),
            new Seed(2L, "ravi",
                    "Fantastic panel for the money, a real bargain at 1440p/144Hz. Colours are "
                            + "solid out of the box and it has worked flawlessly so far."),
            new Seed(2L, "lee",
                    "Picture is fine but the stand wobbles and the menu buttons are awful to use. "
                            + "Nothing has actually failed yet."));

    private final ReviewRepository reviewRepository;
    private final HeuristicReviewScorer heuristicScorer;

    public DataSeeder(ReviewRepository reviewRepository, HeuristicReviewScorer heuristicScorer) {
        this.reviewRepository = reviewRepository;
        this.heuristicScorer = heuristicScorer;
    }

    @Override
    public void run(String... args) {
        if (reviewRepository.count() > 0) {
            return;
        }
        for (Seed seed : SEEDS) {
            Review review = new Review(seed.productId(), seed.author(), seed.text());
            ReviewScore score = heuristicScorer.score(seed.text());
            review.applyScore(
                    score.overallScore(),
                    score.qualityScore(),
                    score.valueScore(),
                    score.reliabilityScore(),
                    Sentiment.parse(score.sentiment()),
                    score.summary(),
                    score.topics(),
                    ScoreSource.FALLBACK,
                    "heuristic",
                    0L);
            reviewRepository.save(review);
        }
    }
}
