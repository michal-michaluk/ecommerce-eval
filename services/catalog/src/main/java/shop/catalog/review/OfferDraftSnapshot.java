package shop.catalog.review;

import java.util.Optional;

/**
 * The contract port that reads the offer as it stands, so a review candidate can be frozen from it (17, 04 RULE-3).
 *
 * <p>It is the consumer's port in the consumer's language: the question is "what does offer {@code offerId} hold right now", the
 * answer is an {@link OfferSnapshot} — values, never the offer's editor and never its draft. {@code Optional} is the whole "not
 * found" vocabulary; the transport turns it into {@code 404} and this context adds no second one.
 *
 * <p>It is asked once when the review is requested, and its {@code content} is frozen into the candidate — never re-read, which is
 * what D38 guarantees and what {@code OfferPublication} receives. Its {@code state} is asked again when the acceptance is decided,
 * because 09 R6/R7 decide on the offer as it is then (09 {@code offerState}).
 */
public interface OfferDraftSnapshot {

    Optional<OfferSnapshot> snapshotOf(String offerId);
}
