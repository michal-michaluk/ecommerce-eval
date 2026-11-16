package shop.catalog.offer;

/**
 * The facts the offer context has recorded, one nested record per fact, in the order the aggregate accepted them.
 *
 * <p>{@link OfferDraftUpdated} is the in-context draft fact and stays inside the context (01 RULE-4); {@link OfferVersionFrozen}
 * carries the description by value, which is what makes the freeze independent of any later edit of the draft (RULE-4);
 * {@link OfferVersionReverted} names the older version it reproduced (RULE-5); {@link OfferReviewRequested} and
 * {@link OfferReviewClosed} are the two ends of one review attempt, and the closure names the attempt 04 decided.
 */
sealed interface OfferEvent {

    record OfferDraftUpdated(String offerId) implements OfferEvent {
    }

    record OfferReviewRequested(String offerId) implements OfferEvent {
    }

    record OfferReviewClosed(String offerId, String reviewId) implements OfferEvent {
    }

    record OfferVersionFrozen(String offerId, int offerVersion, ProductDescription description) implements OfferEvent {
    }

    record OfferVersionReverted(String offerId, int offerVersion, int copiedFrom) implements OfferEvent {
    }

    record OfferRemoved(String offerId) implements OfferEvent {
    }
}
