package shop.catalog.review;

import org.junit.jupiter.api.Test;
import shop.catalog.offer.Offer;

import static shop.catalog.review.PublishDecisionAssert.assertThat;
import static shop.catalog.review.PublishDecision.BlockReason.FINDINGS;
import static shop.catalog.review.PublishDecision.BlockReason.NOT_ACCEPTED;
import static shop.catalog.review.PublishDecision.BlockReason.OFFER_REMOVED;
import static shop.catalog.review.PublishDecision.BlockReason.REJECTED;
import static shop.catalog.review.PublishDecision.BlockReason.WITHDRAWN;
import static shop.catalog.review.ReviewCandidateFixture.candidate;
import static shop.catalog.review.ReviewCandidateFixture.candidateOf;
import static shop.catalog.review.ReviewCandidateFixture.candidatePushedWithAMixedReport;
import static shop.catalog.review.ReviewCandidateFixture.candidatePushedWithAnOverride;
import static shop.catalog.review.ReviewCandidateFixture.candidateWithAStaleOverride;
import static shop.catalog.review.ReviewCandidateFixture.candidateWithBlockingFindings;
import static shop.catalog.review.ReviewCandidateFixture.candidateWithOneBlockingFinding;
import static shop.catalog.review.ReviewCandidateFixture.content;
import static shop.catalog.review.ReviewFixture.AUTHOR;
import static shop.catalog.review.ReviewFixture.acceptedReview;
import static shop.catalog.review.ReviewFixture.publishedReview;
import static shop.catalog.review.ReviewFixture.rejectedReview;
import static shop.catalog.review.ReviewFixture.requestedReview;
import static shop.catalog.review.ReviewFixture.withdrawnReview;

/** The eleven 09 scenarios, plus the two precedence facts the table states but does not spell out as scenarios. */
class PublishDecisionTest {

    @Test
    void s1PublishesAnAcceptedCleanReviewOfADraftOffer() {
        assertThat(PublishDecision.decide(acceptedReview(candidate()), false, Offer.State.DRAFT)).publishes();
    }

    @Test
    void s2BlocksAnAcceptedReviewWithFindingsEvenWhenTheAuthorOverrodeThem() {
        ReviewCandidate pushed = candidatePushedWithAnOverride(AUTHOR);

        assertThat(PublishDecision.decide(acceptedReview(pushed), false, Offer.State.PENDING_REVIEW))
                .isBlockedBy(FINDINGS);
    }

    @Test
    void s3BlocksAnAcceptedReviewWithFindingsAndNoOverride() {
        ReviewCandidate withFindings = candidateOf(content(), ReviewCandidateFixture.blockingFindings());

        assertThat(PublishDecision.decide(acceptedReview(withFindings), false, Offer.State.PENDING_REVIEW))
                .isBlockedBy(FINDINGS);
    }

    @Test
    void s4BlocksARequestedReview() {
        assertThat(PublishDecision.decide(requestedReview(candidate()), false, Offer.State.PENDING_REVIEW))
                .isBlockedBy(NOT_ACCEPTED);
    }

    @Test
    void s5BlocksARejectedReviewThatCarriesOneFinding() {
        assertThat(PublishDecision.decide(rejectedReview(candidateWithOneBlockingFinding()), false, Offer.State.DRAFT))
                .isBlockedBy(REJECTED);
    }

    @Test
    void s6BlocksAWithdrawnReview() {
        assertThat(PublishDecision.decide(withdrawnReview(candidate()), false, Offer.State.DRAFT))
                .isBlockedBy(WITHDRAWN);
    }

    @Test
    void s7ReplaysAnAlreadyPublishedAcceptanceAsANoOp() {
        assertThat(PublishDecision.decide(publishedReview(candidate()), true, Offer.State.PUBLISHED)).doesNothing();
    }

    @Test
    void s8BlocksAnAcceptedReviewOfARemovedOffer() {
        assertThat(PublishDecision.decide(acceptedReview(candidate()), false, Offer.State.REMOVED))
                .isBlockedBy(OFFER_REMOVED);
    }

    @Test
    void s9BlocksOnTheBlockingFindingAndIgnoresTheWarningsAroundItEvenWithAnOverride() {
        ReviewCandidate mixed = candidatePushedWithAMixedReport(AUTHOR);

        assertThat(PublishDecision.decide(acceptedReview(mixed), false, Offer.State.PENDING_REVIEW))
                .isBlockedBy(FINDINGS);
    }

    @Test
    void s10IgnoresAStaleOverrideWhenTheFindingsAreEmpty() {
        ReviewCandidate stale = candidateWithAStaleOverride(AUTHOR);

        assertThat(PublishDecision.decide(acceptedReview(stale), false, Offer.State.DRAFT)).publishes();
    }

    @Test
    void s11PublishesTheNextVersionOfALiveOfferBecauseAReviewNeverHidesIt() {
        assertThat(PublishDecision.decide(acceptedReview(candidate()), false, Offer.State.PUBLISHED)).publishes();
    }

    @Test
    void aRemovedOfferOutranksTheIdempotentReplay() {
        assertThat(PublishDecision.decide(publishedReview(candidate()), true, Offer.State.REMOVED))
                .isBlockedBy(OFFER_REMOVED);
    }

    @Test
    void aFirstPublicationOfAnOfferStillUnderReviewIsPublishable() {
        assertThat(PublishDecision.decide(acceptedReview(candidate()), false, Offer.State.PENDING_REVIEW)).publishes();
    }

    @Test
    void aReviewThatAlreadyCarriesItsVersionIsAReplayWhateverTheCallerSays() {
        ReviewCandidate withFindings = candidateWithBlockingFindings();

        assertThat(PublishDecision.decide(publishedReview(withFindings), false, Offer.State.PUBLISHED))
                .doesNothing();
    }
}
