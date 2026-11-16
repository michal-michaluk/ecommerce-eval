package shop.catalog.review;

/**
 * The facts one review records, one nested record per fact, in the order the process accepted them (04, 09).
 *
 * <p>Every fact carries the {@code reviewId}, so a consumer never has to infer which attempt it reads; {@code offerId} comes first
 * because that is the identifier the rest of the catalog uses for the same value. {@link ReviewAccepted} repeats 04's
 * {@code override} payload, so a consumer can tell a pushed acceptance without loading the review. {@link ReviewPublicationRecorded}
 * makes the freeze observable once per review (RULE-4, D34) and {@link ReviewPublicationBlocked} is 09's other outcome — the fact
 * the read side and 13 consume instead of an offer version (09 {@code PublishBlocked}).
 */
sealed interface ReviewEvent {

    record ReviewRequested(String offerId, String reviewId) implements ReviewEvent {
    }

    record ReviewAccepted(String offerId, String reviewId, ReviewOverride override) implements ReviewEvent {
    }

    record ReviewRejected(String offerId, String reviewId, String reason) implements ReviewEvent {
    }

    record ReviewWithdrawn(String offerId, String reviewId) implements ReviewEvent {
    }

    record ReviewPublicationRecorded(String offerId, String reviewId, int offerVersion) implements ReviewEvent {
    }

    record ReviewPublicationBlocked(
            String offerId, String reviewId, PublishDecision.BlockReason reason) implements ReviewEvent {
    }
}
