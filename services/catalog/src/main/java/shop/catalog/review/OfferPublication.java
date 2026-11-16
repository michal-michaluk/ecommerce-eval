package shop.catalog.review;

import java.time.Instant;

/**
 * The contract port that hands the frozen candidate over to be published (17, 04 RULE-4, 02 RULE-4, D38).
 *
 * <p>The offer context owns the version: it decides the next number and freezes the content. The review context never writes the
 * offer and never asks it to re-read its draft — accepting a review publishes <em>the candidate</em>, so an edit made after the
 * request cannot become the published content (D38). {@code offerId} names the offer because the candidate is the reviewed content
 * and nothing else: an offer identifier is not part of what a reviewer approves, so it travels beside the candidate, not inside it.
 *
 * <p>The answer is the frozen version number, which the review records once per {@code reviewId} (RULE-4, D34). The call, the
 * acceptance and that record belong to one transaction: a failure inside the freeze rolls the acceptance back, and the retry is
 * safe because 09 R6 makes the replay a no-op.
 */
public interface OfferPublication {

    int publish(String offerId, ReviewCandidate candidate, Instant publishedAt, String publishedBy);
}
