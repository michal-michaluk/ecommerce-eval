package shop.catalog.review;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import shop.catalog.review.Review.State;

import java.util.List;

import static shop.catalog.review.ReviewAssert.assertRefused;
import static shop.catalog.review.ReviewAssert.assertThat;
import static shop.catalog.review.ReviewCandidateFixture.blockingFindings;
import static shop.catalog.review.ReviewCandidateFixture.candidate;
import static shop.catalog.review.ReviewCandidateFixture.candidateOf;
import static shop.catalog.review.ReviewCandidateFixture.candidatePushedWithAnOverride;
import static shop.catalog.review.ReviewCandidateFixture.candidateWhoseOverrideIgnoresOnlyOneFinding;
import static shop.catalog.review.ReviewCandidateFixture.candidateWithBlockingFindings;
import static shop.catalog.review.ReviewCandidateFixture.candidateWithWarningsOnly;
import static shop.catalog.review.ReviewCandidateFixture.descriptionOfInstanceA;
import static shop.catalog.review.ReviewCandidateFixture.editedContent;
import static shop.catalog.review.ReviewCandidateFixture.editedDescription;
import static shop.catalog.review.ReviewFixture.AUTHOR;
import static shop.catalog.review.ReviewFixture.NEXT_REVIEW_ID;
import static shop.catalog.review.ReviewFixture.OVERRIDE_DISABLED;
import static shop.catalog.review.ReviewFixture.REASON;
import static shop.catalog.review.ReviewFixture.REVIEWER;
import static shop.catalog.review.ReviewFixture.REVIEWED_AT;
import static shop.catalog.review.ReviewFixture.aDecidedReviewOfTheOffer;
import static shop.catalog.review.ReviewFixture.aRejectedReview;
import static shop.catalog.review.ReviewFixture.anOpenReviewOfTheOffer;
import static shop.catalog.review.ReviewFixture.nextRequest;
import static shop.catalog.review.ReviewFixture.noEarlierReviewOfTheOffer;
import static shop.catalog.review.ReviewFixture.request;
import static shop.catalog.review.ReviewFixture.requestReview;

/** The 04 rules that make a command fail or let it through: RULE-1, RULE-2, RULE-4, RULE-5, RULE-6, RULE-7 and RULE-8. */
class ReviewProcessRulesTest {

    @Test
    void aSecondRequestWhileAReviewIsOpenIsRefused() {
        assertRefused(ReviewException.Error.REVIEW_ALREADY_OPEN,
                () -> ReviewProcess.requestReview(nextRequest(candidate()), anOpenReviewOfTheOffer(), true));
    }

    @Test
    void aRequestReusingAnExistingReviewIdIsRefused() {
        List<Review> earlier = aDecidedReviewOfTheOffer();

        assertRefused(ReviewException.Error.REVIEW_ID_REUSED,
                () -> ReviewProcess.requestReview(request(), earlier, true));
    }

    @Test
    void aRequestAfterTheEarlierReviewWasDecidedIsAllowed() {
        ReviewProcess next = ReviewProcess.requestReview(nextRequest(candidate()), aDecidedReviewOfTheOffer(), true);

        assertThat(next).hasState(State.REQUESTED).hasReviewId(NEXT_REVIEW_ID);
    }

    @Test
    void theAuthorCannotAcceptTheirOwnRequest() {
        ReviewProcess review = requestReview(request());

        assertRefused(ReviewException.Error.REVIEWER_IS_REQUESTER, () -> review.accept(AUTHOR, REVIEWED_AT));
        assertThat(review).hasState(State.REQUESTED).hasNoReviewer();
    }

    @Test
    void theAuthorCannotRejectTheirOwnRequest() {
        ReviewProcess review = requestReview(request());

        assertRefused(ReviewException.Error.REVIEWER_IS_REQUESTER, () -> review.reject(AUTHOR, REVIEWED_AT, REASON));
    }

    @Test
    void aPublicationCannotBeRecordedBeforeTheReviewIsAccepted() {
        assertRefused(ReviewException.Error.PUBLICATION_NEEDS_ACCEPTANCE,
                () -> requestReview(request()).recordPublication(1));
    }

    @Test
    void aRejectionWithoutAReasonIsRefused() {
        ReviewProcess review = requestReview(request());

        assertRefused(ReviewException.Error.REJECTION_REASON_MISSING, () -> review.reject(REVIEWER, REVIEWED_AT, " "));
        assertThat(review).hasState(State.REQUESTED).hasNoReason();
    }

    @Test
    void onlyTheAuthorMayWithdrawTheRequest() {
        ReviewProcess review = requestReview(request());

        assertRefused(ReviewException.Error.WITHDRAWAL_NEEDS_REQUESTER, () -> review.withdraw(REVIEWER));
        assertThat(review).hasState(State.REQUESTED).isOpen();
    }

    @Test
    void aReviewNeverCarriesOverIntoTheNextAttempt() {
        ReviewProcess rejected = aRejectedReview();
        ReviewProcess next = ReviewProcess.requestReview(
                nextRequest(candidateOf(editedContent())), List.of(rejected.toReview()), true);

        assertThat(rejected).hasState(State.REJECTED).hasCandidateDescription(descriptionOfInstanceA());
        assertThat(next).hasState(State.REQUESTED).hasReviewId(NEXT_REVIEW_ID).hasCandidateDescription(editedDescription());
    }

    @Test
    void aCandidateWithBlockingFindingsCannotBeRequestedWithoutAnOverride() {
        assertRefused(ReviewException.Error.FINDINGS_NEED_OVERRIDE,
                () -> requestReview(request(candidateWithBlockingFindings())));
    }

    @Test
    void anOverrideLetsTheAuthorPushTheReviewAndIsRecordedOnTheCandidate() {
        ReviewCandidate pushed = candidatePushedWithAnOverride(AUTHOR);

        ReviewProcess review = requestReview(request(pushed));

        assertThat(review).hasState(State.REQUESTED).hasCandidate(pushed);
        Assertions.assertThat(review.toReview().candidate().override().ignoredFindings())
                .isEqualTo(blockingFindings());
    }

    @Test
    void anOverrideRecordedBySomeoneElseIsRefused() {
        ReviewCandidate pushedByTheReviewer = candidatePushedWithAnOverride(REVIEWER);

        assertRefused(ReviewException.Error.OVERRIDE_NOT_BY_REQUESTER,
                () -> requestReview(request(pushedByTheReviewer)));
    }

    @Test
    void anOverrideThatIgnoresLessThanTheBlockingReportIsRefused() {
        ReviewCandidate partial = candidateWhoseOverrideIgnoresOnlyOneFinding(AUTHOR);

        assertRefused(ReviewException.Error.OVERRIDE_INCOMPLETE, () -> requestReview(request(partial)));
    }

    @Test
    void anOverrideIsRefusedWhenTheOverrideIsSwitchedOff() {
        ReviewCandidate pushed = candidatePushedWithAnOverride(AUTHOR);

        assertRefused(ReviewException.Error.OVERRIDE_DISABLED,
                () -> ReviewProcess.requestReview(request(pushed), noEarlierReviewOfTheOffer(), OVERRIDE_DISABLED));
    }

    @Test
    void aCleanRequestNeedsNoOverrideEvenWhenTheOverrideIsSwitchedOff() {
        ReviewProcess review = ReviewProcess.requestReview(
                request(), noEarlierReviewOfTheOffer(), OVERRIDE_DISABLED);

        assertThat(review).hasState(State.REQUESTED).hasCandidate(candidate());
    }

    @Test
    void warningsAloneNeedNoOverride() {
        ReviewProcess review = requestReview(request(candidateWithWarningsOnly()));

        assertThat(review).hasCandidate(candidateWithWarningsOnly());
        Assertions.assertThat(review.toReview().candidate().hasOverride()).isFalse();
    }

    @Test
    void aReviewProcessIsNotReachableWithAnEmptyRequest() {
        assertRefused(ReviewException.Error.VALUE_MISSING,
                () -> ReviewProcess.requestReview(null, noEarlierReviewOfTheOffer(), true));
    }
}
