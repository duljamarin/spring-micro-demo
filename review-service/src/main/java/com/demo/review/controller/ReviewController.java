package com.demo.review.controller;

import com.demo.review.dto.ProductScoreResponse;
import com.demo.review.dto.ReviewResponse;
import com.demo.review.dto.ScoringEngineResponse;
import com.demo.review.dto.SubmitReviewRequest;
import com.demo.review.service.ReviewService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Everything the React scoreboard talks to, reached through the gateway at
 * /api/reviews/**.
 *
 * Note what is missing: there is no endpoint that takes a prompt. The model
 * is an implementation detail of scoring a review - clients send review text
 * and get scores back, and swapping llama3.2 for qwen2.5, or for the keyword
 * fallback, changes nothing about this contract.
 */
@RestController
@RequestMapping("/api/reviews")
public class ReviewController {

    private final ReviewService reviewService;

    public ReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    /**
     * Submits and scores in one call. This is a slow POST by design - it
     * blocks on a local model generating tokens, typically 1-5s on CPU - so
     * every timeout between here and the browser is sized for it (see the
     * ladder documented in review-service.yml and api-gateway.yml).
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ReviewResponse submit(@Valid @RequestBody SubmitReviewRequest request) {
        return reviewService.submit(request);
    }

    /** Re-runs the scorer on text already stored. See ReviewService.rescore. */
    @PostMapping("/{id}/rescore")
    public ReviewResponse rescore(@PathVariable Long id) {
        return reviewService.rescore(id);
    }

    @GetMapping
    public List<ReviewResponse> list(@RequestParam(required = false) Long productId) {
        return productId == null ? reviewService.findAll() : reviewService.findByProduct(productId);
    }

    @GetMapping("/{id}")
    public ReviewResponse byId(@PathVariable Long id) {
        return reviewService.findById(id);
    }

    /** The scoreboard: one aggregated row per product. */
    @GetMapping("/scores")
    public List<ProductScoreResponse> scores() {
        return reviewService.scoreboard();
    }

    @GetMapping("/scores/{productId}")
    public ProductScoreResponse productScore(@PathVariable Long productId) {
        return reviewService.productScore(productId);
    }

    /**
     * Which scorer is live. The UI polls this so it can say "model offline,
     * scores below came from the fallback" instead of presenting degraded
     * output as if nothing were wrong.
     */
    @GetMapping("/engine")
    public ScoringEngineResponse engine() {
        return reviewService.engine();
    }
}
