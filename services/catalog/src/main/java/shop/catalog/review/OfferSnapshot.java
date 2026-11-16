package shop.catalog.review;

import shop.catalog.offer.Offer;

/**
 * The offer as the review context reads it at one instant: its lifecycle state and the content a candidate would freeze from it
 * (02, 04 RULE-3).
 *
 * <p>Both halves are needed for different reasons and at different times. {@link #content()} is read once and frozen into the
 * candidate, because content must not drift between the request and the publication (D38); {@link #state()} is read again when the
 * decision is taken, because 09 R3/R6/R7 ask what the offer is <em>now</em> — an offer removed while its candidate waits must not
 * be published (09 R7, D37).
 *
 * <p>They travel together because the review context reaches the offer through one contract port: a second port for the state alone
 * would be a second crossing of the same edge ({@code boundary.one-wire-per-edge}), and 09's {@code offerState} input has no other
 * source inside this context.
 */
public record OfferSnapshot(Offer.State state, OfferContent content) {

    public OfferSnapshot {
        ReviewException.ensureValue(state, "state");
        ReviewException.ensureValue(content, "content");
    }
}
