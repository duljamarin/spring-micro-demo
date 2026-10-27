package com.demo.review.model;

import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * A customer review plus the scores derived from it.
 *
 * The scores are stored, not recomputed on read: an LLM call costs seconds
 * and is non-deterministic, so re-scoring on every page load would make the
 * scoreboard both slow and unstable. Scoring happens once, at submit time.
 */
@Entity
@Table(name = "reviews")
public class Review {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long productId;

    @Column(nullable = false)
    private String author;

    @Column(nullable = false, length = 2000)
    private String text;

    @Column(nullable = false)
    private Instant submittedAt;

    @Column(nullable = false)
    private int overallScore;

    @Column(nullable = false)
    private int qualityScore;

    @Column(nullable = false)
    private int valueScore;

    @Column(nullable = false)
    private int reliabilityScore;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Sentiment sentiment;

    @Column(length = 500)
    private String summary;

    @ElementCollection(fetch = FetchType.EAGER)
    private List<String> topics = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ScoreSource scoreSource;

    /** Model id that produced the score, or "heuristic" for the fallback. */
    @Column(nullable = false)
    private String scoredBy;

    /** Wall-clock cost of the scoring call, surfaced in the UI. */
    @Column(nullable = false)
    private long scoringMillis;

    protected Review() {
    }

    public Review(Long productId, String author, String text) {
        this.productId = productId;
        this.author = author;
        this.text = text;
        this.submittedAt = Instant.now();
    }

    /**
     * Applies a scorer's verdict. Clamping lives here rather than in the
     * scorers so that no path - LLM, fallback or a future one - can put an
     * out-of-range number in the database: a model that answers "9" on a
     * 0-100 scale, or "150", would otherwise poison every average computed
     * from it.
     */
    public void applyScore(int overall, int quality, int value, int reliability,
                           Sentiment sentiment, String summary, List<String> topics,
                           ScoreSource source, String scoredBy, long scoringMillis) {
        this.overallScore = clamp(overall);
        this.qualityScore = clamp(quality);
        this.valueScore = clamp(value);
        this.reliabilityScore = clamp(reliability);
        this.sentiment = sentiment == null ? Sentiment.NEUTRAL : sentiment;
        this.summary = truncate(summary, 500);
        this.topics = topics == null ? new ArrayList<>() : new ArrayList<>(topics);
        this.scoreSource = source;
        this.scoredBy = scoredBy;
        this.scoringMillis = scoringMillis;
    }

    private static int clamp(int score) {
        return Math.max(0, Math.min(100, score));
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }

    public Long getId() {
        return id;
    }

    public Long getProductId() {
        return productId;
    }

    public String getAuthor() {
        return author;
    }

    public String getText() {
        return text;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }

    public int getOverallScore() {
        return overallScore;
    }

    public int getQualityScore() {
        return qualityScore;
    }

    public int getValueScore() {
        return valueScore;
    }

    public int getReliabilityScore() {
        return reliabilityScore;
    }

    public Sentiment getSentiment() {
        return sentiment;
    }

    public String getSummary() {
        return summary;
    }

    public List<String> getTopics() {
        return topics;
    }

    public ScoreSource getScoreSource() {
        return scoreSource;
    }

    public String getScoredBy() {
        return scoredBy;
    }

    public long getScoringMillis() {
        return scoringMillis;
    }
}
