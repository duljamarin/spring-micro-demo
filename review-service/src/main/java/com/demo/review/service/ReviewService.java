package com.demo.review.service;

import com.demo.review.ScoringProperties;
import com.demo.review.client.ProductCatalog;
import com.demo.review.client.ProductDto;
import com.demo.review.dto.ProductScoreResponse;
import com.demo.review.dto.ReviewResponse;
import com.demo.review.dto.ScoringEngineResponse;
import com.demo.review.dto.SubmitReviewRequest;
import com.demo.review.exception.ReviewNotFoundException;
import com.demo.review.model.Review;
import com.demo.review.model.Sentiment;
import com.demo.review.repository.ReviewRepository;
import com.demo.review.scoring.LlmReviewScorer;
import com.demo.review.scoring.OllamaHealthProbe;
import com.demo.review.scoring.ReviewScoringService;
import com.demo.review.scoring.ScoringOutcome;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ReviewService {

    private final ReviewRepository reviewRepository;
    private final ReviewScoringService scoringService;
    private final ScoreAggregator aggregator;
    private final ProductCatalog productCatalog;
    private final OllamaHealthProbe healthProbe;
    private final LlmReviewScorer llmScorer;
    private final ScoringProperties properties;

    public ReviewService(ReviewRepository reviewRepository,
                         ReviewScoringService scoringService,
                         ScoreAggregator aggregator,
                         ProductCatalog productCatalog,
                         OllamaHealthProbe healthProbe,
                         LlmReviewScorer llmScorer,
                         ScoringProperties properties) {
        this.reviewRepository = reviewRepository;
        this.scoringService = scoringService;
        this.aggregator = aggregator;
        this.productCatalog = productCatalog;
        this.healthProbe = healthProbe;
        this.llmScorer = llmScorer;
        this.properties = properties;
    }

    /**
     * Scores the review, then saves it.
     *
     * Not {@code @Transactional}: a transaction held open across a
     * multi-second LLM call would pin a connection from the pool for the
     * whole generation, and a handful of concurrent submits would exhaust
     * the pool while the CPU is busy generating. The scoring call has no
     * database work in it, so there is nothing to roll back - the only write
     * is the single save at the end.
     */
    public ReviewResponse submit(SubmitReviewRequest request) {
        String productName = productCatalog.nameOf(request.productId())
                .orElse("Product #" + request.productId());

        ScoringOutcome outcome = scoringService.score(productName, request.text());

        Review review = new Review(request.productId(), request.author(), request.text());
        review.applyScore(
                outcome.score().overallScore(),
                outcome.score().qualityScore(),
                outcome.score().valueScore(),
                outcome.score().reliabilityScore(),
                Sentiment.parse(outcome.score().sentiment()),
                outcome.score().summary(),
                outcome.score().topics(),
                outcome.source(),
                outcome.scoredBy(),
                outcome.millis());

        return ReviewResponse.from(reviewRepository.save(review), productName);
    }

    /**
     * Re-runs scoring on a review that is already stored.
     *
     * This is what makes the LLM integration visible rather than theoretical:
     * the seeded reviews start out scored by the keyword fallback, and
     * re-scoring one with the model running shows, on the same input text,
     * exactly what the model adds - a real summary, real topics, and aspect
     * scores that stop defaulting to 50.
     *
     * Also the honest repair path for reviews submitted while ollama was
     * down, which would otherwise be stuck with fallback scores forever.
     */
    public ReviewResponse rescore(Long id) {
        Review review = reviewRepository.findById(id).orElseThrow(() -> new ReviewNotFoundException(id));
        String productName = productCatalog.nameOf(review.getProductId())
                .orElse("Product #" + review.getProductId());

        ScoringOutcome outcome = scoringService.score(productName, review.getText());
        review.applyScore(
                outcome.score().overallScore(),
                outcome.score().qualityScore(),
                outcome.score().valueScore(),
                outcome.score().reliabilityScore(),
                Sentiment.parse(outcome.score().sentiment()),
                outcome.score().summary(),
                outcome.score().topics(),
                outcome.source(),
                outcome.scoredBy(),
                outcome.millis());

        return ReviewResponse.from(reviewRepository.save(review), productName);
    }

    @Transactional(readOnly = true)
    public List<ReviewResponse> findByProduct(Long productId) {
        Map<Long, String> names = catalogNames();
        return reviewRepository.findByProductIdOrderBySubmittedAtDesc(productId).stream()
                .map(review -> ReviewResponse.from(review, displayName(names, productId)))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ReviewResponse> findAll() {
        Map<Long, String> names = catalogNames();
        return reviewRepository.findAllByOrderBySubmittedAtDesc().stream()
                .map(review -> ReviewResponse.from(review, displayName(names, review.getProductId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public ReviewResponse findById(Long id) {
        Review review = reviewRepository.findById(id).orElseThrow(() -> new ReviewNotFoundException(id));
        return ReviewResponse.from(review, displayName(catalogNames(), review.getProductId()));
    }

    /**
     * The scoreboard.
     *
     * Every catalog product gets a row, including ones with no reviews yet -
     * an empty row is information ("nobody has reviewed the dock"), and
     * silently omitting it would make the UI look broken right after a fresh
     * start. Products that have reviews but are no longer in the catalog
     * (deleted, or product-service down) get a row too, so scores never
     * vanish from the board.
     */
    @Transactional(readOnly = true)
    public List<ProductScoreResponse> scoreboard() {
        Map<Long, List<Review>> byProduct = reviewRepository.findAll().stream()
                .collect(Collectors.groupingBy(Review::getProductId));

        List<ProductDto> catalog = productCatalog.catalog();
        Map<Long, String> names = catalog.stream()
                .collect(Collectors.toMap(ProductDto::id, ProductDto::name, (a, b) -> a, LinkedHashMap::new));

        List<ProductScoreResponse> rows = new ArrayList<>();
        for (ProductDto product : catalog) {
            rows.add(aggregator.aggregate(product.id(), product.name(),
                    byProduct.getOrDefault(product.id(), List.of())));
        }
        byProduct.keySet().stream()
                .filter(productId -> !names.containsKey(productId))
                .sorted()
                .forEach(productId -> rows.add(aggregator.aggregate(productId,
                        displayName(names, productId), byProduct.get(productId))));

        // Reviewed products first, best score first; unreviewed ones trail
        // behind in catalog order rather than being mixed in at score 0.
        rows.sort(Comparator
                .comparing((ProductScoreResponse row) -> row.reviewCount() == 0)
                .thenComparing(row -> -row.overallScore()));
        return rows;
    }

    @Transactional(readOnly = true)
    public ProductScoreResponse productScore(Long productId) {
        List<Review> reviews = reviewRepository.findByProductIdOrderBySubmittedAtDesc(productId);
        return aggregator.aggregate(productId, displayName(catalogNames(), productId), reviews);
    }

    public ScoringEngineResponse engine() {
        OllamaHealthProbe.Status status = properties.isLlmEnabled()
                ? healthProbe.probe(llmScorer.modelId())
                : new OllamaHealthProbe.Status(false, "LLM scoring is switched off (scoring.llm-enabled=false)");

        return new ScoringEngineResponse(
                properties.isLlmEnabled(),
                status.reachable(),
                llmScorer.modelId(),
                healthProbe.baseUrl(),
                "heuristic keyword scorer",
                status.detail());
    }

    public long reviewCount() {
        return reviewRepository.count();
    }

    private Map<Long, String> catalogNames() {
        return productCatalog.catalog().stream()
                .collect(Collectors.toMap(ProductDto::id, ProductDto::name, (a, b) -> a));
    }

    private static String displayName(Map<Long, String> names, Long productId) {
        return names.getOrDefault(productId, "Product #" + productId);
    }
}
