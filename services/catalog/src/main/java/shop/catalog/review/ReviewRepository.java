package shop.catalog.review;

import java.util.List;
import java.util.Optional;

/**
 * The one secondary port of the review process: the adapter rehydrates the process and saves it back, whatever shape it stores.
 *
 * <p>{@link #reviewsOf} is the input of the two admission rules the process cannot answer alone — at most one open review per offer
 * (RULE-1) and a new {@code reviewId} for every attempt (RULE-7). It returns the offer's own attempts as snapshots, never a
 * projection row: the rules read states and identifiers, not the content of a screen.
 */
interface ReviewRepository {

    Optional<ReviewProcess> get(String reviewId);

    List<Review> reviewsOf(String offerId);

    void save(ReviewProcess review);
}
