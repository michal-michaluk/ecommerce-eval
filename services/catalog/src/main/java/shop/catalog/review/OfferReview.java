package shop.catalog.review;

import java.time.Instant;

/**
 * The contract port that asks the offer to enter review — pattern 4 of {@code context-boundaries.md}: the consumer states the
 * command in its own vocabulary and the producer's own service decides it.
 *
 * <p>Asking is what 17 RULE-3 requires: the review never mutates the offer, and the price of entering a review — 02 RULE-1's one
 * primary image, the contiguous positions, an offer that is not removed — is the offer's rule, not this context's. A refusal
 * therefore travels back as the offer's own {@code reason} (D49), so 12 answers the client with the vocabulary the domain speaks and
 * no second list is invented here.
 *
 * <p>The command carries the attempt and its moment because it is written in the consumer's language: a review knows which
 * {@code reviewId} it opens and when. The offer records its own fact ("this offer is under review") and derives nothing from them
 * today, so the values are its copy of who asked, not a rule it applies.
 *
 * <p>It is called before the candidate is frozen and inside the caller's transaction (D34, 17 R4): an offer that refuses leaves no
 * frozen candidate and no half-opened review behind, and an offer that accepts is under review exactly when the review exists.
 */
public interface OfferReview {

    void requestReview(String offerId, String reviewId, Instant at);
}
