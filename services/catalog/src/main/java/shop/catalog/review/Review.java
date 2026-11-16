package shop.catalog.review;

import java.time.Instant;

/**
 * One review as the read side serves it: who asked, what was frozen, who decided and which version the acceptance published.
 *
 * <p>It is the process's snapshot — the review <em>as it stands</em>, with the candidate embedded by value, so a reader sees the
 * content that was actually reviewed and not the offer's current draft (RULE-3). {@code publishedOfferVersion} is {@code null}
 * until RULE-4's freeze has been recorded and never changes afterwards.
 */
public record Review(
        String reviewId,
        String offerId,
        State state,
        String requestedBy,
        Instant requestedAt,
        ReviewCandidate candidate,
        String reviewedBy,
        Instant reviewedAt,
        String reason,
        Integer publishedOfferVersion) {

    public Review {
        ReviewException.ensureValue(reviewId, "reviewId");
        ReviewException.ensureValue(offerId, "offerId");
        ReviewException.ensureValue(state, "state");
        ReviewException.ensureValue(requestedBy, "requestedBy");
        ReviewException.ensureValue(requestedAt, "requestedAt");
        ReviewException.ensureValue(candidate, "candidate");
    }

    /** The only state a decision can still act on (RULE-6); {@code accepted}, {@code rejected} and {@code withdrawn} are terminal. */
    public boolean isOpen() {
        return state == State.REQUESTED;
    }

    public boolean isPublished() {
        return publishedOfferVersion != null;
    }

    /**
     * {@code requested → accepted | rejected | withdrawn} — all three exits are terminal; a new attempt is a new
     * {@code reviewId}, never a reopened review (RULE-7).
     */
    public enum State {
        REQUESTED,
        ACCEPTED,
        REJECTED,
        WITHDRAWN
    }
}
