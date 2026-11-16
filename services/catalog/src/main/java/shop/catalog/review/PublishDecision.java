package shop.catalog.review;

import shop.catalog.offer.Offer;

/**
 * Element 09 — the decision an accepted review takes: does it freeze a new offer version, is it a replay, or is it blocked and why.
 *
 * <pre>
 * decide(review, alreadyPublished, offerState)
 *   requested                     → BLOCK (not accepted)          R3
 *   rejected                      → BLOCK (rejected)              R4
 *   withdrawn                     → BLOCK (withdrawn)             R5
 *   accepted, offer removed       → BLOCK (offer removed)         R7
 *   accepted, alreadyPublished    → NO-OP                         R6  (idempotent replay, D15)
 *   accepted, blocking finding    → BLOCK (findings)              R2
 *   accepted, clean               → PUBLISH                       R1
 * </pre>
 *
 * <p>The table is a precedence chain, not a match: a terminal state outranks a replay (R7 before R6), and a replay outranks the
 * findings, because a review that already published cannot publish again whatever the report now says. A review that carries a
 * {@code publishedOfferVersion} is a replay whether or not the caller says so, so the two facts that can name it can never
 * disagree in the publishing direction. {@code alreadyPublished} is per {@code reviewId}, never per offer: replaying one acceptance
 * is a no-op (D15), while a <em>new</em> review after a successful one publishes the next version — including for an offer that is
 * live, because a review never hides it (D37, S11).
 *
 * <p>There is no override here (D27, F15): a candidate that carries an author's override is still blocked by R2, so the reviewer
 * can only reject it or ask for a fix. Warnings never block — only {@link ReviewFinding.Severity#BLOCKING} findings do (08 QP-8,
 * QP-12), which is why the findings arrive as they were frozen and the severity is not recomputed.
 *
 * <p>It answers, it never fails and it reads nothing: the review state, the frozen findings, the replay flag and the offer state
 * are its whole input, so the decision is reproducible and testable without a container.
 */
record PublishDecision(Outcome outcome, BlockReason reason) {

    PublishDecision {
        ReviewException.ensureValue(outcome, "outcome");
        boolean complete = outcome == Outcome.BLOCK ? reason != null : reason == null;
        if (!complete) {
            throw new ReviewException(ReviewException.Error.DECISION_INCOMPLETE, outcome);
        }
    }

    static PublishDecision decide(Review review, boolean alreadyPublished, Offer.State offerState) {
        if (review.state() == Review.State.REQUESTED) {
            return blockedBy(BlockReason.NOT_ACCEPTED);
        }
        if (review.state() == Review.State.REJECTED) {
            return blockedBy(BlockReason.REJECTED);
        }
        if (review.state() == Review.State.WITHDRAWN) {
            return blockedBy(BlockReason.WITHDRAWN);
        }
        if (offerState == Offer.State.REMOVED) {
            return blockedBy(BlockReason.OFFER_REMOVED);
        }
        if (alreadyPublished || review.isPublished()) {
            return noOp();
        }
        return review.candidate().hasBlockingFindings() ? blockedBy(BlockReason.FINDINGS) : publish();
    }

    static PublishDecision publish() {
        return new PublishDecision(Outcome.PUBLISH, null);
    }

    static PublishDecision noOp() {
        return new PublishDecision(Outcome.NO_OP, null);
    }

    static PublishDecision blockedBy(BlockReason reason) {
        return new PublishDecision(Outcome.BLOCK, reason);
    }

    boolean isPublishable() {
        return outcome == Outcome.PUBLISH;
    }

    /** The three outcomes 09 names; only {@link #PUBLISH} freezes a version, the other two leave the offer as it is. */
    enum Outcome {
        PUBLISH,
        BLOCK,
        NO_OP
    }

    /** Why the decision refused to publish; it travels with the value, so no caller matches on a message. */
    enum BlockReason {
        NOT_ACCEPTED,
        REJECTED,
        WITHDRAWN,
        OFFER_REMOVED,
        FINDINGS
    }
}
