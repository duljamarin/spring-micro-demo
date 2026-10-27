package com.demo.review.scoring;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Keyword scorer used when the LLM can't answer.
 *
 * It exists so the feature degrades instead of disappearing: with ollama not
 * installed, not running, or still pulling a model, the scoreboard stays
 * populated and the whole stack is still demonstrable. Every score it
 * produces is tagged {@code FALLBACK} so nobody mistakes it for model output.
 *
 * It is not trying to be good. It is trying to be instant, dependency-free
 * and deterministic - which also makes it the only scorer that can be unit
 * tested without mocking a model.
 */
@Component
public class HeuristicReviewScorer {

    private static final List<String> POSITIVE_WORDS = List.of(
            "great", "excellent", "love", "perfect", "amazing", "fast", "solid",
            "recommend", "happy", "sturdy", "worth", "bargain", "flawless", "quiet");

    private static final List<String> NEGATIVE_WORDS = List.of(
            "broke", "broken", "terrible", "awful", "hate", "slow", "cheap",
            "flimsy", "disappointed", "refund", "overpriced", "rattle", "noisy",
            "stopped", "useless", "waste");

    /**
     * Aspect keywords. A review that never mentions an aspect must score 50
     * for it (unknown), not inherit the overall score - otherwise "arrived
     * dented" would silently drag down a value-for-money number the reviewer
     * never commented on.
     */
    private static final Map<String, List<String>> ASPECT_WORDS = Map.of(
            "quality", List.of("quality", "build", "material", "sturdy", "flimsy", "plastic", "solid", "finish"),
            "value", List.of("price", "value", "worth", "cheap", "expensive", "overpriced", "bargain", "money"),
            "reliability", List.of("works", "broke", "broken", "stopped", "reliable", "failed", "crash", "defect", "lasted"));

    public ReviewScore score(String reviewText) {
        String text = reviewText == null ? "" : reviewText.toLowerCase(Locale.ROOT);
        List<String> words = List.of(text.split("[^a-z]+"));

        int positives = countMatches(words, POSITIVE_WORDS);
        int negatives = countMatches(words, NEGATIVE_WORDS);

        int overall = polarityToScore(positives, negatives);

        return new ReviewScore(
                overall,
                sentimentLabel(overall),
                aspectScore(text, words, "quality", overall),
                aspectScore(text, words, "value", overall),
                aspectScore(text, words, "reliability", overall),
                "Scored without the language model: keyword sentiment only.",
                topics(text));
    }

    private static int countMatches(List<String> words, List<String> vocabulary) {
        return (int) words.stream().filter(vocabulary::contains).count();
    }

    /**
     * Maps a (positive, negative) word count onto 0-100 centred on 50. The
     * divisor keeps a single strong word from pinning the score to an
     * extreme - three matching words in one direction is what it takes to
     * reach the ends of the scale.
     */
    private static int polarityToScore(int positives, int negatives) {
        int net = positives - negatives;
        int raw = 50 + (net * 50) / 3;
        return Math.max(0, Math.min(100, raw));
    }

    /**
     * 50 - "no signal" - unless the review actually mentions the aspect, in
     * which case it gets the overall polarity. Half the point of keeping this
     * scorer around is that its shortcomings are legible.
     */
    private static int aspectScore(String text, List<String> words, String aspect, int overall) {
        boolean mentioned = ASPECT_WORDS.get(aspect).stream()
                .anyMatch(keyword -> words.contains(keyword) || text.contains(keyword));
        return mentioned ? overall : 50;
    }

    private static String sentimentLabel(int overall) {
        if (overall >= 60) {
            return "POSITIVE";
        }
        return overall <= 40 ? "NEGATIVE" : "NEUTRAL";
    }

    private static List<String> topics(String text) {
        List<String> topics = new ArrayList<>();
        ASPECT_WORDS.forEach((aspect, keywords) -> {
            if (keywords.stream().anyMatch(text::contains)) {
                topics.add(aspect);
            }
        });
        if (topics.isEmpty()) {
            topics.add("general");
        }
        return topics;
    }
}
