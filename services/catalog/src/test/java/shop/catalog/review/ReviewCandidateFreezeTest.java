package shop.catalog.review;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import shop.catalog.offer.Offer;

import java.util.ArrayList;
import java.util.List;

import static shop.catalog.review.ReviewAssert.assertThat;
import static shop.catalog.review.ReviewCandidateFixture.AVAILABILITY_START;
import static shop.catalog.review.ReviewCandidateFixture.blockingFindings;
import static shop.catalog.review.ReviewCandidateFixture.candidateOf;
import static shop.catalog.review.ReviewCandidateFixture.content;
import static shop.catalog.review.ReviewCandidateFixture.contentWithMedia;
import static shop.catalog.review.ReviewCandidateFixture.descriptionOfInstanceA;
import static shop.catalog.review.ReviewCandidateFixture.editedContent;
import static shop.catalog.review.ReviewCandidateFixture.mediaOfInstanceA;
import static shop.catalog.review.ReviewCandidateFixture.overrideBy;
import static shop.catalog.review.ReviewFixture.AUTHOR;
import static shop.catalog.review.ReviewFixture.FIRST_OFFER_VERSION;
import static shop.catalog.review.ReviewFixture.OFFER_ID;
import static shop.catalog.review.ReviewFixture.REVIEWER;
import static shop.catalog.review.ReviewFixture.REVIEWED_AT;
import static shop.catalog.review.ReviewFixture.noEarlierReviewOfTheOffer;
import static shop.catalog.review.ReviewFixture.request;
import static shop.catalog.review.ReviewFixture.requestReview;

/**
 * RULE-3 and D38 through the three contract ports: the candidate is frozen from one answer of {@link OfferDraftSnapshot}, 08 judges
 * exactly that content, and {@link OfferPublication} receives the candidate — never a re-read draft — when the review is accepted.
 * The offer state is the one fact that is read live, because 09 R7 decides on the offer as it is then.
 */
class ReviewCandidateFreezeTest {

    @Test
    void theCandidateKeepsTheContentTheOfferHeldWhenTheReviewWasRequested() {
        FakeOfferDraftSnapshot offers = FakeOfferDraftSnapshot.holding(content());

        ReviewCandidate candidate = candidateOf(offers.snapshotOf(OFFER_ID).orElseThrow().content());
        offers.offer(OFFER_ID, editedContent());

        Assertions.assertThat(candidate.description()).isEqualTo(descriptionOfInstanceA());
        Assertions.assertThat(candidate.media()).isEqualTo(mediaOfInstanceA());
        Assertions.assertThat(candidate.availabilityStart()).isEqualTo(AVAILABILITY_START);
    }

    @Test
    void theOfferIsAskedOnceAndNeverReRead() {
        FakeOfferDraftSnapshot offers = FakeOfferDraftSnapshot.holding(content());

        ReviewProcess review = freeze(offers);
        offers.offer(OFFER_ID, editedContent());
        review.accept(REVIEWER, REVIEWED_AT);

        Assertions.assertThat(offers.reads()).isEqualTo(1);
        Assertions.assertThat(review.toReview().candidate().description()).isEqualTo(descriptionOfInstanceA());
    }

    @Test
    void theCandidateCopiesTheContentItFreezesInsteadOfViewingItLive() {
        List<ReviewMedia> media = new ArrayList<>(mediaOfInstanceA());

        ReviewCandidate candidate = candidateOf(contentWithMedia(descriptionOfInstanceA(), media));
        media.clear();

        Assertions.assertThat(candidate.media()).isEqualTo(mediaOfInstanceA());
        Assertions.assertThat(candidate.media()).hasSize(3);
    }

    @Test
    void acceptancePublishesTheFrozenCandidateAndRecordsItsVersionOnce() {
        FakeOfferDraftSnapshot offers = FakeOfferDraftSnapshot.holding(content());
        FakeOfferPublication publication = new FakeOfferPublication();

        ReviewProcess review = freeze(offers);
        offers.offer(OFFER_ID, editedContent());
        review.accept(REVIEWER, REVIEWED_AT);
        int frozenVersion = publication.publish(OFFER_ID, review.toReview().candidate(), REVIEWED_AT, REVIEWER);
        review.recordPublication(frozenVersion);

        Assertions.assertThat(publication.lastPublished().description()).isEqualTo(descriptionOfInstanceA());
        Assertions.assertThat(publication.publications()).isEqualTo(1);
        assertThat(review).hasPublishedVersion(FIRST_OFFER_VERSION);
        Assertions.assertThat(offers.reads()).isEqualTo(1);
    }

    @Test
    void theOfferStateIsReadLiveWhileTheContentStaysFrozen() {
        FakeOfferDraftSnapshot offers = FakeOfferDraftSnapshot.holding(content());
        ReviewProcess review = freeze(offers);
        review.accept(REVIEWER, REVIEWED_AT);
        offers.offer(OFFER_ID, Offer.State.REMOVED, editedContent());

        Offer.State liveState = offers.snapshotOf(OFFER_ID).orElseThrow().state();

        PublishDecisionAssert.assertThat(PublishDecision.decide(review.toReview(), false, liveState))
                .isBlockedBy(PublishDecision.BlockReason.OFFER_REMOVED);
        Assertions.assertThat(review.toReview().candidate().description()).isEqualTo(descriptionOfInstanceA());
    }

    @Test
    void theQualityPortJudgesTheSameContentTheCandidateFreezes() {
        FakeQualityFindings quality = FakeQualityFindings.answeringWith(blockingFindings());
        OfferContent content = content();

        List<ReviewFinding> findings = quality.findingsFor(content);
        ReviewCandidate candidate = candidateOf(content, findings, overrideBy(AUTHOR, findings));

        Assertions.assertThat(quality.evaluated()).containsExactly(content);
        assertThat(requestReview(request(candidate))).hasCandidate(candidate);
    }

    private static ReviewProcess freeze(FakeOfferDraftSnapshot offers) {
        OfferContent content = offers.snapshotOf(OFFER_ID).orElseThrow().content();
        return ReviewProcess.requestReview(request(candidateOf(content)), noEarlierReviewOfTheOffer(), true);
    }
}
