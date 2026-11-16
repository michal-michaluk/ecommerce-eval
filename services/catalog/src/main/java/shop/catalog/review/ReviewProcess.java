package shop.catalog.review;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import shop.catalog.review.Review.State;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Element 04 — one review of one offer: what is being reviewed, who asked, who decided and which version the acceptance
 * published. A process, so it has instances and a lifecycle rather than an editable configuration.
 *
 * <p>The lifecycle is {@code requested → accepted | rejected | withdrawn} and all three exits are terminal; a new attempt is a new
 * {@code reviewId} (RULE-7), never a reopened review. RULE-1 keeps at most one review open per offer, RULE-2 keeps the reviewer
 * away from their own request, RULE-3 freezes the candidate at the request so a later draft edit cannot change what is being
 * reviewed, RULE-4 makes acceptance the publication trigger and records the frozen version exactly once per review, RULE-5 keeps
 * the candidate of a rejected review for audit, RULE-6 lets only the requester withdraw and only while the review is open.
 *
 * <p>RULE-8 has two halves and only one of them lives here: the <em>request</em> may carry findings when the author also carries an
 * override that is enabled, recorded by the author and names every finding it ignores; acceptance never does, so a reviewer cannot
 * force a publication past a blocking finding — that is 09 R2's decision ({@link PublishDecision}), not a command guard (D27, F15).
 *
 * <p>What the aggregate does not do: it never reads a clock, never queries a port and never publishes. The candidate arrives frozen
 * from {@link OfferDraftSnapshot} and {@link QualityFindings}, the moment and the identifiers arrive in {@link ReviewRequest},
 * the frozen version arrives from {@link OfferPublication}, and the offer state 09 needs arrives as a value — all inside the
 * caller's transaction (D34).
 *
 * <p>Instances are created through {@link #requestReview(ReviewRequest, List, boolean)}; the aggregate is package-private and
 * appends every accepted change to its {@code events} buffer, which the persistence adapter drains. Rehydration uses the same
 * constructor the factory does and emits nothing.
 */
@AllArgsConstructor(access = AccessLevel.PACKAGE)
class ReviewProcess {

    final String reviewId;
    final List<ReviewEvent> events;

    private final String offerId;
    private final String requestedBy;
    private final Instant requestedAt;
    private final ReviewCandidate candidate;
    private State state;
    private String reviewedBy;
    private Instant reviewedAt;
    private String reason;
    private Integer publishedOfferVersion;

    /**
     * Opens a review of the candidate {@code request} carries, refusing a second open review of the same offer (RULE-1), a reuse of
     * an attempt that already exists (RULE-7) and an override the catalog does not allow, that the author did not record, or that
     * ignores less than the report it overrides (RULE-8).
     *
     * <p>{@code earlierReviews} is the offer's own history as the caller knows it — the earlier attempts, whatever their state. It is
     * the only rule of this aggregate that needs facts beyond the review, and it needs them as values, so no repository is reached
     * from here. {@code overrideEnabled} is {@code catalog.publish.override.enabled} as a value (D27, D29): with it off a request
     * may not carry findings at all.
     */
    static ReviewProcess requestReview(
            ReviewRequest request, List<Review> earlierReviews, boolean overrideEnabled) {
        ReviewException.ensureValue(request, "request");
        var review = new ReviewProcess(request.reviewId(), new ArrayList<>(), request.offerId(),
                request.requestedBy(), request.requestedAt(), request.candidate(), State.REQUESTED,
                null, null, null, null);
        review.ensureTheAttemptIsAdmissible(earlierReviews, overrideEnabled);
        review.events.add(new ReviewEvent.ReviewRequested(request.offerId(), request.reviewId()));
        return review;
    }

    /** Accepts the review and thereby triggers publication (RULE-4); the reviewer must not be the author (RULE-2). */
    void accept(String reviewer, Instant reviewedAt) {
        ReviewException.ensureValue(reviewer, "reviewer");
        ReviewException.ensureValue(reviewedAt, "reviewedAt");
        ensureReviewIsOpen();
        ensureReviewerDiffersFromRequester(reviewer);

        this.state = State.ACCEPTED;
        this.reviewedBy = reviewer;
        this.reviewedAt = reviewedAt;
        events.add(new ReviewEvent.ReviewAccepted(offerId, reviewId, candidate.override()));
    }

    /** Refuses the review with a free-text reason; the candidate stays for audit (RULE-5). */
    void reject(String reviewer, Instant reviewedAt, String reason) {
        ReviewException.ensureValue(reviewer, "reviewer");
        ReviewException.ensureValue(reviewedAt, "reviewedAt");
        ensureReviewIsOpen();
        ensureReviewerDiffersFromRequester(reviewer);
        ensureReasonIsGiven(reason);

        this.state = State.REJECTED;
        this.reviewedBy = reviewer;
        this.reviewedAt = reviewedAt;
        this.reason = reason.strip();
        events.add(new ReviewEvent.ReviewRejected(offerId, reviewId, this.reason));
    }

    /** Takes the request back; only its author may do it and only while it is still open (RULE-6). */
    void withdraw(String requester) {
        ReviewException.ensureValue(requester, "requester");
        ensureReviewIsOpen();
        ensureWithdrawalIsByRequester(requester);

        this.state = State.WITHDRAWN;
        events.add(new ReviewEvent.ReviewWithdrawn(offerId, reviewId));
    }

    /**
     * Records the version the acceptance just froze on the offer — once per review (RULE-4, D34). The caller publishes the
     * candidate through {@link OfferPublication} first and passes the number back, so an accepted review always names its version.
     */
    void recordPublication(int offerVersion) {
        ensureReviewIsAccepted();
        ensureNoPublicationWasRecorded();
        ensureOfferVersionIsNumbered(offerVersion);

        this.publishedOfferVersion = offerVersion;
        events.add(new ReviewEvent.ReviewPublicationRecorded(offerId, reviewId, offerVersion));
    }

    /**
     * Records that 09 refused to publish the accepted review, and why (09 {@code PublishBlocked}).
     *
     * <p>It is a fact, not a transition: the review stays accepted and the reason is the decision's own, so the read side and 13
     * learn what happened instead of inferring it from a missing version.
     */
    void recordBlock(PublishDecision.BlockReason reason) {
        ReviewException.ensureValue(reason, "reason");
        ensureReviewIsAccepted();
        events.add(new ReviewEvent.ReviewPublicationBlocked(offerId, reviewId, reason));
    }

    /** The process as the read side serves it: the candidate by value, the decision and the version it published, if any. */
    Review toReview() {
        Review snapshot = new Review(reviewId, offerId, state, requestedBy, requestedAt, candidate,
                reviewedBy, reviewedAt, reason, publishedOfferVersion);
        return snapshot;
    }

    private void ensureTheAttemptIsAdmissible(List<Review> earlierReviews, boolean overrideEnabled) {
        ReviewException.ensureValue(earlierReviews, "earlierReviews");
        ensureNoReviewIsOpenForTheOffer(earlierReviews);
        ensureTheAttemptIsNew(earlierReviews);
        ensureOverrideRecordsWhatItMust(overrideEnabled);
    }

    private void ensureNoReviewIsOpenForTheOffer(List<Review> earlierReviews) {
        Optional<Review> openReview = earlierReviews.stream().filter(Review::isOpen).findFirst();
        if (openReview.isPresent()) {
            throw new ReviewException(ReviewException.Error.REVIEW_ALREADY_OPEN, offerId, openReview.get().reviewId());
        }
    }

    private void ensureTheAttemptIsNew(List<Review> earlierReviews) {
        boolean alreadyRequested = earlierReviews.stream().anyMatch(earlier -> earlier.reviewId().equals(reviewId));
        if (alreadyRequested) {
            throw new ReviewException(ReviewException.Error.REVIEW_ID_REUSED, reviewId);
        }
    }

    private void ensureOverrideRecordsWhatItMust(boolean overrideEnabled) {
        ensureTheOverrideIsEnabled(overrideEnabled);
        ensureTheOverrideIsByTheRequester();
        ensureFindingsAreOverridden();
        ensureTheOverrideNamesEveryBlockingFinding();
    }

    private void ensureTheOverrideIsEnabled(boolean overrideEnabled) {
        if (!overrideEnabled && candidate.hasOverride()) {
            throw new ReviewException(ReviewException.Error.OVERRIDE_DISABLED, reviewId);
        }
    }

    private void ensureTheOverrideIsByTheRequester() {
        if (candidate.hasOverride() && !requestedBy.equals(candidate.override().actor())) {
            throw new ReviewException(ReviewException.Error.OVERRIDE_NOT_BY_REQUESTER,
                    reviewId, candidate.override().actor(), requestedBy);
        }
    }

    private void ensureFindingsAreOverridden() {
        if (candidate.hasBlockingFindings() && !candidate.hasOverride()) {
            throw new ReviewException(ReviewException.Error.FINDINGS_NEED_OVERRIDE, reviewId);
        }
    }

    private void ensureTheOverrideNamesEveryBlockingFinding() {
        if (candidate.hasOverride() && !candidate.overrideCoversBlockingFindings()) {
            throw new ReviewException(ReviewException.Error.OVERRIDE_INCOMPLETE, reviewId);
        }
    }

    private void ensureReviewIsOpen() {
        if (state != State.REQUESTED) {
            throw new ReviewException(ReviewException.Error.REVIEW_NOT_OPEN, reviewId, state);
        }
    }

    private void ensureReviewerDiffersFromRequester(String reviewer) {
        if (requestedBy.equals(reviewer)) {
            throw new ReviewException(ReviewException.Error.REVIEWER_IS_REQUESTER, reviewId, requestedBy);
        }
    }

    private void ensureReasonIsGiven(String reason) {
        if (reason == null || reason.isBlank()) {
            throw new ReviewException(ReviewException.Error.REJECTION_REASON_MISSING, reviewId);
        }
    }

    private void ensureWithdrawalIsByRequester(String requester) {
        if (!requestedBy.equals(requester)) {
            throw new ReviewException(ReviewException.Error.WITHDRAWAL_NEEDS_REQUESTER, reviewId, requestedBy, requester);
        }
    }

    private void ensureReviewIsAccepted() {
        if (state != State.ACCEPTED) {
            throw new ReviewException(ReviewException.Error.PUBLICATION_NEEDS_ACCEPTANCE, reviewId, state);
        }
    }

    private void ensureNoPublicationWasRecorded() {
        if (publishedOfferVersion != null) {
            throw new ReviewException(ReviewException.Error.PUBLICATION_ALREADY_RECORDED, reviewId, publishedOfferVersion);
        }
    }

    private void ensureOfferVersionIsNumbered(int offerVersion) {
        if (offerVersion < 1) {
            throw new ReviewException(ReviewException.Error.OFFER_VERSION_INVALID, offerVersion);
        }
    }
}
