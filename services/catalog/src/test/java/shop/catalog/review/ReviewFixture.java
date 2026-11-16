package shop.catalog.review;

import shop.catalog.offer.Offer;
import shop.catalog.review.Review.State;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static shop.catalog.review.ReviewCandidateFixture.candidate;

/** The reviews the 04 and 09 rules talk about: one per lifecycle state, the attempts that must be refused, and the snapshots. */
public class ReviewFixture {

    public static final String OFFER_ID = "PRD-75423";
    public static final String REVIEW_ID = "RV-12";
    public static final String NEXT_REVIEW_ID = "RV-13";
    public static final String AUTHOR = "author.nowak";
    public static final String REVIEWER = "reviewer.kowalska";
    public static final Instant REQUESTED_AT = Instant.parse("2025-06-14T07:10:00Z");
    public static final Instant REVIEWED_AT = Instant.parse("2025-06-15T07:58:00Z");
    public static final String REASON = "Brak EAN i niepełny opis";
    public static final int FIRST_OFFER_VERSION = 1;
    public static final boolean OVERRIDE_ENABLED = true;
    public static final boolean OVERRIDE_DISABLED = false;

    private ReviewFixture() {
    }

    public static ReviewRequest request() {
        return request(candidate());
    }

    public static ReviewRequest request(ReviewCandidate candidate) {
        return new ReviewRequest(REVIEW_ID, OFFER_ID, AUTHOR, REQUESTED_AT, candidate);
    }

    public static ReviewRequest nextRequest(ReviewCandidate candidate) {
        return new ReviewRequest(NEXT_REVIEW_ID, OFFER_ID, AUTHOR, REQUESTED_AT, candidate);
    }

    /** A request for one attempt of one offer, for a test that needs both identifiers of its own. */
    public static ReviewRequest request(String reviewId, String offerId, ReviewCandidate candidate) {
        return request(reviewId, offerId, REQUESTED_AT, candidate);
    }

    /** The same request at a moment of the test's choosing, so the offer's attempt history has an order. */
    public static ReviewRequest request(String reviewId, String offerId, Instant requestedAt, ReviewCandidate candidate) {
        return new ReviewRequest(reviewId, offerId, AUTHOR, requestedAt, candidate);
    }

    /** An attempt no other test has used: a committed test leaves its rows behind, and RULE-7 never reuses a review id. */
    public static String aNewReviewId() {
        return "RV-" + UUID.randomUUID();
    }

    /** An offer no other test has used, so RULE-1's history of one offer stays inside one test. */
    public static String aNewOfferId() {
        return "PRD-" + UUID.randomUUID();
    }

    public static ReviewProcess aRequestedReview() {
        return requestReview(request());
    }

    public static ReviewProcess requestReview(ReviewRequest request) {
        return requestReview(request, OVERRIDE_ENABLED);
    }

    public static ReviewProcess requestReview(ReviewRequest request, boolean overrideEnabled) {
        return ReviewProcess.requestReview(request, noEarlierReviewOfTheOffer(), overrideEnabled);
    }

    public static ReviewProcess anAcceptedReview() {
        ReviewProcess review = aRequestedReview();
        review.accept(REVIEWER, REVIEWED_AT);
        return review;
    }

    public static ReviewProcess aRejectedReview() {
        ReviewProcess review = aRequestedReview();
        review.reject(REVIEWER, REVIEWED_AT, REASON);
        return review;
    }

    public static ReviewProcess aWithdrawnReview() {
        ReviewProcess review = aRequestedReview();
        review.withdraw(AUTHOR);
        return review;
    }

    public static ReviewProcess aPublishedReview() {
        ReviewProcess review = anAcceptedReview();
        review.recordPublication(FIRST_OFFER_VERSION);
        return review;
    }

    public static ReviewProcess rehydrated(Review saved) {
        return new ReviewProcess(saved.reviewId(), new ArrayList<>(), saved.offerId(),
                saved.requestedBy(), saved.requestedAt(), saved.candidate(), saved.state(),
                saved.reviewedBy(), saved.reviewedAt(), saved.reason(), saved.publishedOfferVersion());
    }

    public static List<Review> noEarlierReviewOfTheOffer() {
        return List.of();
    }

    public static List<Review> anOpenReviewOfTheOffer() {
        return List.of(aRequestedReview().toReview());
    }

    public static List<Review> aDecidedReviewOfTheOffer() {
        return List.of(aRejectedReview().toReview());
    }

    public static Review requestedReview(ReviewCandidate candidate) {
        return reviewOf(State.REQUESTED, candidate, null, null, null);
    }

    public static Review acceptedReview(ReviewCandidate candidate) {
        return reviewOf(State.ACCEPTED, candidate, REVIEWER, REVIEWED_AT, null);
    }

    public static Review publishedReview(ReviewCandidate candidate) {
        return new Review(REVIEW_ID, OFFER_ID, State.ACCEPTED, AUTHOR, REQUESTED_AT, candidate,
                REVIEWER, REVIEWED_AT, null, FIRST_OFFER_VERSION);
    }

    public static Review rejectedReview(ReviewCandidate candidate) {
        return reviewOf(State.REJECTED, candidate, REVIEWER, REVIEWED_AT, REASON);
    }

    public static Review withdrawnReview(ReviewCandidate candidate) {
        return reviewOf(State.WITHDRAWN, candidate, null, null, null);
    }

    public static OfferSnapshot offerHolding(Offer.State state, OfferContent content) {
        return new OfferSnapshot(state, content);
    }

    public static ReviewEvent accepted(ReviewOverride override) {
        return new ReviewEvent.ReviewAccepted(OFFER_ID, REVIEW_ID, override);
    }

    public static ReviewEvent publicationRecorded() {
        return new ReviewEvent.ReviewPublicationRecorded(OFFER_ID, REVIEW_ID, FIRST_OFFER_VERSION);
    }

    public static ReviewEvent publicationBlocked() {
        return new ReviewEvent.ReviewPublicationBlocked(
                OFFER_ID, REVIEW_ID, PublishDecision.BlockReason.FINDINGS);
    }

    /** The three facts one acceptance publishes, in the order the process accepted them (RULE-4, D34, 09). */
    public static ReviewEvent[] aPublishedHistory(String offerId, String reviewId, int offerVersion) {
        return new ReviewEvent[]{
                new ReviewEvent.ReviewRequested(offerId, reviewId),
                new ReviewEvent.ReviewAccepted(offerId, reviewId, null),
                new ReviewEvent.ReviewPublicationRecorded(offerId, reviewId, offerVersion)};
    }

    /** The same acceptance when 09 refused to publish it: the block replaces the version (09 {@code PublishBlocked}). */
    public static ReviewEvent[] aBlockedHistory(String offerId, String reviewId, PublishDecision.BlockReason reason) {
        return new ReviewEvent[]{
                new ReviewEvent.ReviewRequested(offerId, reviewId),
                new ReviewEvent.ReviewAccepted(offerId, reviewId, null),
                new ReviewEvent.ReviewPublicationBlocked(offerId, reviewId, reason)};
    }

    private static Review reviewOf(
            State state, ReviewCandidate candidate, String reviewedBy, Instant reviewedAt, String reason) {
        return new Review(REVIEW_ID, OFFER_ID, state, AUTHOR, REQUESTED_AT, candidate,
                reviewedBy, reviewedAt, reason, null);
    }
}
