package com.demo.review.exception;

public class ReviewNotFoundException extends RuntimeException {

    public ReviewNotFoundException(Long id) {
        super("Review " + id + " not found");
    }
}
