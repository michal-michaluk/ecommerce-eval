package shop.catalog.review;

import java.time.Instant;

/**
 * The command that opens a review: which offer, which attempt (a new {@code reviewId} every time), who asks, when, and the
 * candidate the application froze from {@link OfferContent} and {@link QualityFindings} a moment earlier.
 *
 * <p>The identifiers and the moment arrive as values — the aggregate owns no clock and no generator — so the same request always
 * produces the same review.
 */
public record ReviewRequest(
        String reviewId, String offerId, String requestedBy, Instant requestedAt, ReviewCandidate candidate) {

    public ReviewRequest {
        ReviewException.ensureValue(reviewId, "reviewId");
        ReviewException.ensureValue(offerId, "offerId");
        ReviewException.ensureValue(requestedBy, "requestedBy");
        ReviewException.ensureValue(requestedAt, "requestedAt");
        ReviewException.ensureValue(candidate, "candidate");
    }
}
