package shop.catalog.review;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import shop.catalog.review.Review.State;
import shop.catalog.review.ReviewEvent.ReviewRequested;
import shop.catalog.review.ReviewEvent.ReviewWithdrawn;

import static shop.catalog.review.ReviewAssert.assertRefused;
import static shop.catalog.review.ReviewAssert.assertThat;
import static shop.catalog.review.ReviewCandidateFixture.blockingFindings;
import static shop.catalog.review.ReviewCandidateFixture.candidate;
import static shop.catalog.review.ReviewCandidateFixture.candidateOf;
import static shop.catalog.review.ReviewCandidateFixture.content;
import static shop.catalog.review.ReviewCandidateFixture.overrideBy;
import static shop.catalog.review.ReviewFixture.AUTHOR;
import static shop.catalog.review.ReviewFixture.FIRST_OFFER_VERSION;
import static shop.catalog.review.ReviewFixture.OFFER_ID;
import static shop.catalog.review.ReviewFixture.REASON;
import static shop.catalog.review.ReviewFixture.REQUESTED_AT;
import static shop.catalog.review.ReviewFixture.REVIEWER;
import static shop.catalog.review.ReviewFixture.REVIEWED_AT;
import static shop.catalog.review.ReviewFixture.REVIEW_ID;
import static shop.catalog.review.ReviewFixture.aPublishedReview;
import static shop.catalog.review.ReviewFixture.aRejectedReview;
import static shop.catalog.review.ReviewFixture.aRequestedReview;
import static shop.catalog.review.ReviewFixture.aWithdrawnReview;
import static shop.catalog.review.ReviewFixture.accepted;
import static shop.catalog.review.ReviewFixture.anAcceptedReview;
import static shop.catalog.review.ReviewFixture.noEarlierReviewOfTheOffer;
import static shop.catalog.review.ReviewFixture.publicationBlocked;
import static shop.catalog.review.ReviewFixture.publicationRecorded;
import static shop.catalog.review.ReviewFixture.rehydrated;
import static shop.catalog.review.ReviewFixture.request;
import static shop.catalog.review.ReviewFixture.requestReview;

/** The 04 lifecycle: requested → accepted | rejected | withdrawn, all three terminal, and one event per state change. */
class ReviewProcessLifecycleTest {

    @Test
    void aRequestOpensTheReviewOfTheFrozenCandidate() {
        ReviewProcess review = aRequestedReview();

        assertThat(review)
                .hasState(State.REQUESTED)
                .isOpen()
                .hasReviewId(REVIEW_ID)
                .hasOfferId(OFFER_ID)
                .wasRequestedBy(AUTHOR, REQUESTED_AT)
                .hasNoReviewer()
                .hasCandidate(candidate());
    }

    @Test
    void aRequestIsRecordedOnce() {
        ReviewProcess review = ReviewProcess.requestReview(
                request(), noEarlierReviewOfTheOffer(), ReviewFixture.OVERRIDE_ENABLED);

        Assertions.assertThat(review.events).containsExactly(new ReviewRequested(OFFER_ID, REVIEW_ID));
    }

    @Test
    void acceptanceRecordsTheReviewer() {
        assertThat(anAcceptedReview()).hasState(State.ACCEPTED).wasReviewedBy(REVIEWER, REVIEWED_AT);
    }

    @Test
    void acceptanceIsRecordedOnce() {
        ReviewProcess review = ReviewProcess.requestReview(
                request(), noEarlierReviewOfTheOffer(), ReviewFixture.OVERRIDE_ENABLED);
        review.events.clear();

        review.accept(REVIEWER, REVIEWED_AT);

        Assertions.assertThat(review.events).containsExactly(accepted(null));
    }

    @Test
    void acceptanceRepeatsTheOverrideThatOpenedTheReview() {
        ReviewOverride override = overrideBy(AUTHOR, blockingFindings());
        ReviewProcess review = requestReview(request(candidateOf(content(), blockingFindings(), override)));
        review.events.clear();

        review.accept(REVIEWER, REVIEWED_AT);

        Assertions.assertThat(review.events).containsExactly(accepted(override));
    }

    @Test
    void rejectionRecordsTheReasonAndKeepsTheCandidate() {
        assertThat(aRejectedReview())
                .hasState(State.REJECTED)
                .hasReason(REASON)
                .wasReviewedBy(REVIEWER, REVIEWED_AT)
                .hasCandidate(candidate())
                .hasNoPublishedVersion();
    }

    @Test
    void withdrawalKeepsTheCandidateAndTheRequest() {
        assertThat(aWithdrawnReview())
                .hasState(State.WITHDRAWN)
                .wasRequestedBy(AUTHOR, REQUESTED_AT)
                .hasNoReviewer()
                .hasCandidate(candidate());
    }

    @Test
    void withdrawalIsRecordedOnce() {
        ReviewProcess review = ReviewProcess.requestReview(
                request(), noEarlierReviewOfTheOffer(), ReviewFixture.OVERRIDE_ENABLED);
        review.events.clear();

        review.withdraw(AUTHOR);

        Assertions.assertThat(review.events).containsExactly(new ReviewWithdrawn(OFFER_ID, REVIEW_ID));
    }

    @Test
    void anAcceptedReviewCannotBeDecidedAgain() {
        ReviewProcess review = anAcceptedReview();

        assertRefused(ReviewException.Error.REVIEW_NOT_OPEN, () -> review.accept(REVIEWER, REVIEWED_AT));
        assertRefused(ReviewException.Error.REVIEW_NOT_OPEN, () -> review.reject(REVIEWER, REVIEWED_AT, REASON));
        assertRefused(ReviewException.Error.REVIEW_NOT_OPEN, () -> review.withdraw(AUTHOR));
    }

    @Test
    void aWithdrawnReviewCannotBeAccepted() {
        ReviewProcess review = aWithdrawnReview();

        assertRefused(ReviewException.Error.REVIEW_NOT_OPEN, () -> review.accept(REVIEWER, REVIEWED_AT));
        assertThat(review).hasState(State.WITHDRAWN).hasNoReviewer();
    }

    @Test
    void anAcceptedReviewRecordsTheFrozenVersionOnce() {
        ReviewProcess review = anAcceptedReview();

        review.recordPublication(FIRST_OFFER_VERSION);

        assertThat(review).hasPublishedVersion(FIRST_OFFER_VERSION);
        assertRefused(ReviewException.Error.PUBLICATION_ALREADY_RECORDED,
                () -> review.recordPublication(FIRST_OFFER_VERSION + 1));
    }

    @Test
    void aPublicationVersionBelowOneIsRefused() {
        assertRefused(ReviewException.Error.OFFER_VERSION_INVALID, () -> anAcceptedReview().recordPublication(0));
    }

    @Test
    void theRecordedPublicationIsTheOnlyEventAfterTheAcceptance() {
        ReviewProcess review = anAcceptedReview();
        review.events.clear();

        review.recordPublication(FIRST_OFFER_VERSION);

        Assertions.assertThat(review.events).containsExactly(publicationRecorded());
    }

    @Test
    void aBlockedPublicationIsRecordedAsItsOwnFact() {
        ReviewProcess review = anAcceptedReview();
        review.events.clear();

        review.recordBlock(PublishDecision.BlockReason.FINDINGS);

        Assertions.assertThat(review.events).containsExactly(publicationBlocked());
        assertThat(review).hasState(State.ACCEPTED).hasNoPublishedVersion();
    }

    @Test
    void onlyAnAcceptedReviewCanRecordABlock() {
        assertRefused(ReviewException.Error.PUBLICATION_NEEDS_ACCEPTANCE,
                () -> aRequestedReview().recordBlock(PublishDecision.BlockReason.NOT_ACCEPTED));
    }

    @Test
    void aPublishedReviewStillNamesItsVersionAfterTheRejectionAttempt() {
        ReviewProcess review = aPublishedReview();

        assertRefused(ReviewException.Error.REVIEW_NOT_OPEN, () -> review.reject(REVIEWER, REVIEWED_AT, REASON));
        assertThat(review).hasState(State.ACCEPTED).hasPublishedVersion(FIRST_OFFER_VERSION);
    }

    @Test
    void aRehydratedReviewIsTheOneThatWasSavedAndEmitsNothing() {
        Review saved = aPublishedReview().toReview();

        ReviewProcess rehydrated = rehydrated(saved);

        assertThat(rehydrated)
                .hasState(State.ACCEPTED)
                .hasPublishedVersion(FIRST_OFFER_VERSION)
                .hasCandidate(saved.candidate());
        Assertions.assertThat(rehydrated.events).isEmpty();
    }
}
