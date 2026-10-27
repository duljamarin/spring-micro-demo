package com.demo.review.scoring;

import java.util.List;

/**
 * The shape we ask the LLM to fill in.
 *
 * This record is the contract: Spring AI derives a JSON schema from it,
 * appends that schema to the prompt and binds the model's reply back into an
 * instance. So the field names here are effectively part of the prompt -
 * renaming {@code valueScore} to {@code vs} would measurably degrade the
 * answers, because the name is the only hint the model gets about meaning.
 *
 * {@code sentiment} is a String rather than the {@link com.demo.review.model.Sentiment}
 * enum on purpose: a small local model answers "Positive" or "mixed" often
 * enough that strict enum binding would fail the whole call over a label we
 * can map perfectly well ourselves (see {@code Sentiment.parse}).
 */
public record ReviewScore(
        int overallScore,
        String sentiment,
        int qualityScore,
        int valueScore,
        int reliabilityScore,
        String summary,
        List<String> topics
) {
}
