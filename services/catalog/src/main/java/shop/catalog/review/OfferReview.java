package shop.catalog.review;

import java.time.Instant;

/**
 * The contract port that asks the offer to enter review and to leave it again — pattern 4 of {@code context-boundaries.md}: the
 * consumer states the command in its own vocabulary and the producer's own service decides it.
 *
 * <p>Asking is what 17 RULE-3 requires: the review never mutates the offer, and the price of entering a review — 02 RULE-1's one
 * primary image, the contiguous positions, an offer that is not removed — is the offer's rule, not this context's. A refusal
 * therefore travels back as the offer's own {@code reason} (D49), so 12 answers the client with the vocabulary the domain speaks and
 * no second list is invented here.
 *
 * <p>The attempt is the edge's whole vocabulary: {@link #requestReview} opens it before anything is frozen and
 * {@link #closeReview} ends it whenever it ends without a version — rejected, withdrawn, or blocked by 09. Which of those it was
 * stays in this context, because the offer's own rule is the same for all three — a draft returns to {@code draft}, a published or
 * removed offer keeps its state (04 RULE-5, D37, 02 RULE-6) — and an acceptance that publishes never closes through here: the freeze
 * does it.
 *
 * <p>Opening carries the moment because it is written in the consumer's language: a review knows which {@code reviewId} it opens and
 * when. The offer records its own fact ("this offer is under review" / "the attempt ended") and derives nothing from the values,
 * so they are its copy of who asked, not a rule it applies.
 *
 * <p>Both calls run inside the caller's transaction (D34, 17 R4): an offer that refuses leaves no frozen candidate and no half-opened
 * review behind, and a decision that fails leaves the offer under review exactly as it was.
 */
public interface OfferReview {

    void requestReview(String offerId, String reviewId, Instant at);

    void closeReview(String offerId, String reviewId);
}
