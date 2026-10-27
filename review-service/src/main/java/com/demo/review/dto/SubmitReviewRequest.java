package com.demo.review.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record SubmitReviewRequest(
        @NotNull @Positive Long productId,

        @NotBlank @Size(max = 80) String author,

        /**
         * A floor as well as a ceiling: "ok" carries no signal for any scorer,
         * and rejecting it at the edge is cheaper and clearer than spending a
         * multi-second model call to find that out.
         */
        @NotBlank @Size(min = 15, max = 2000) String text
) {
}
