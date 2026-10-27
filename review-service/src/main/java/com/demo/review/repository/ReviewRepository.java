package com.demo.review.repository;

import com.demo.review.model.Review;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReviewRepository extends JpaRepository<Review, Long> {

    List<Review> findByProductIdOrderBySubmittedAtDesc(Long productId);

    List<Review> findAllByOrderBySubmittedAtDesc();
}
