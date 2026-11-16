package shop.catalog.review;

import org.junit.jupiter.api.Test;
import shop.catalog.offer.Offer;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static shop.catalog.review.ReviewCandidateFixture.candidate;
import static shop.catalog.review.ReviewCandidateFixture.candidatePushedWithAnOverride;
import static shop.catalog.review.ReviewCandidateFixture.content;
import static shop.catalog.review.ReviewFixture.AUTHOR;
import static shop.catalog.review.ReviewFixture.FIRST_OFFER_VERSION;
import static shop.catalog.review.ReviewFixture.OFFER_ID;
import static shop.catalog.review.ReviewFixture.REASON;
import static shop.catalog.review.ReviewFixture.REVIEWER;
import static shop.catalog.review.ReviewFixture.REVIEWED_AT;
import static shop.catalog.review.ReviewFixture.REVIEW_ID;
import static shop.catalog.review.ReviewFixture.aDecidedReviewOfTheOffer;
import static shop.catalog.review.ReviewFixture.aPublishedReview;
import static shop.catalog.review.ReviewFixture.aRequestedReview;
import static shop.catalog.review.ReviewFixture.request;

/**
 * The primary port against a fake repository and the four contract ports: every use case is one load, one command and one save, and
 * the decisions the service makes are the ones 04 and 09 own — publication on acceptance, the block the decision reports, and the
 * replay that publishes nothing. The request asks the offer first, so an offer that refuses it never sees a frozen candidate.
 */
class ReviewServiceTest {

    final Map<String, ReviewProcess> reviews = new HashMap<>();

    final FakeOfferDraftSnapshot offers = FakeOfferDraftSnapshot.holding(content());

    final FakeOfferPublication publications = new FakeOfferPublication();

    final ReviewService service = ReviewServiceFixture.service(new FakeReviewRepository(), offers, publications);

    @Test
    void requestsAReviewOfTheFrozenCandidateAndTheOfferHistory() {
        Review requested = service.request(request(candidate()));

        ReviewAssert.assertThat(requested)
                .hasReviewId(REVIEW_ID)
                .hasState(Review.State.REQUESTED)
                .hasCandidate(candidate());
        assertThat(reviews).containsKey(REVIEW_ID);
        assertThat(offers.reads()).isZero();
    }

    @Test
    void refusesASecondOpenReviewOfTheSameOffer() {
        reviews.put(REVIEW_ID, aRequestedReview());

        ReviewAssert.assertRefused(ReviewException.Error.REVIEW_ALREADY_OPEN,
                () -> service.request(ReviewFixture.nextRequest(candidate())));
    }

    @Test
    void refusesAReviewIdAnEarlierAttemptAlreadyUsed() {
        reviews.put(REVIEW_ID, ReviewFixture.rehydrated(aDecidedReviewOfTheOffer().get(0)));

        ReviewAssert.assertRefused(ReviewException.Error.REVIEW_ID_REUSED,
                () -> service.request(request(candidate())));
    }

    @Test
    void refusesAnOverrideTheCatalogSwitchedOff() {
        ReviewService withoutOverride = ReviewServiceFixture.service(
                new FakeReviewRepository(), offers, publications, ReviewFixture.OVERRIDE_DISABLED);

        ReviewAssert.assertRefused(ReviewException.Error.OVERRIDE_DISABLED,
                () -> withoutOverride.request(request(candidatePushedWithAnOverride(AUTHOR))));
    }

    @Test
    void publishesTheFrozenCandidateWhenTheReviewIsAccepted() {
        service.request(request(candidate()));

        Review accepted = service.decide(REVIEW_ID, REVIEWER).orElseThrow();

        ReviewAssert.assertThat(accepted)
                .hasState(Review.State.ACCEPTED)
                .wasReviewedBy(REVIEWER, REVIEWED_AT)
                .hasPublishedVersion(FIRST_OFFER_VERSION);
        assertThat(publications.publications()).isEqualTo(1);
        assertThat(publications.lastPublished()).isEqualTo(candidate());
        assertThat(publications.lastOfferId()).isEqualTo(OFFER_ID);
    }

    @Test
    void blocksAnAcceptanceThatCarriesABlockingFindingDespiteItsOverride() {
        FakeOfferReview offerReview = FakeOfferReview.accepting();
        ReviewService blocking = serviceWith(offerReview);
        blocking.request(request(candidatePushedWithAnOverride(AUTHOR)));

        Review accepted = blocking.decide(REVIEW_ID, REVIEWER).orElseThrow();

        ReviewAssert.assertThat(accepted).hasState(Review.State.ACCEPTED).hasNoPublishedVersion();
        assertThat(publications.publications()).isZero();
        assertThat(offerReview.closures()).containsExactly(REVIEW_ID);
    }

    @Test
    void blocksAnAcceptanceOfAnOfferThatIsGone() {
        FakeOfferDraftSnapshot gone = FakeOfferDraftSnapshot.holding(content());
        gone.offer(OFFER_ID, Offer.State.REMOVED, content());
        ReviewService onARemovedOffer = ReviewServiceFixture.service(new FakeReviewRepository(), gone, publications);
        onARemovedOffer.request(request(candidate()));

        Review accepted = onARemovedOffer.decide(REVIEW_ID, REVIEWER).orElseThrow();

        ReviewAssert.assertThat(accepted).hasNoPublishedVersion();
        assertThat(publications.publications()).isZero();
    }

    @Test
    void refusesToAcceptAReviewWhoseOfferItCannotFind() {
        ReviewService onAMissingOffer = ReviewServiceFixture.service(
                new FakeReviewRepository(), new FakeOfferDraftSnapshot(), publications);
        onAMissingOffer.request(request(candidate()));

        ReviewAssert.assertRefused(ReviewException.Error.OFFER_UNKNOWN,
                () -> onAMissingOffer.decide(REVIEW_ID, REVIEWER));
        assertThat(publications.publications()).isZero();
    }

    @Test
    void answersTheOpenReviewOfAnOfferAndNothingOnceItIsDecided() {
        service.request(request(candidate()));

        assertThat(service.openReviewOf(OFFER_ID)).map(Review::reviewId).contains(REVIEW_ID);

        service.reject(REVIEW_ID, REVIEWER, REASON);

        assertThat(service.openReviewOf(OFFER_ID)).isEmpty();
    }

    @Test
    void answersTheReplayOfAnAcceptanceWithTheVersionItAlreadyPublished() {
        service.request(request(candidate()));
        Review published = service.decide(REVIEW_ID, REVIEWER).orElseThrow();

        Review replayed = service.decide(REVIEW_ID, REVIEWER).orElseThrow();

        assertThat(replayed).isEqualTo(published);
        ReviewAssert.assertThat(replayed).hasPublishedVersion(FIRST_OFFER_VERSION);
        assertThat(publications.publications()).isEqualTo(1);
    }

    @Test
    void rejectsTheReviewWithItsReasonAndPublishesNothing() {
        service.request(request(candidate()));

        Review rejected = service.reject(REVIEW_ID, REVIEWER, REASON).orElseThrow();

        ReviewAssert.assertThat(rejected)
                .hasState(Review.State.REJECTED)
                .hasReason(REASON)
                .hasCandidate(candidate())
                .hasNoPublishedVersion();
        assertThat(publications.publications()).isZero();
    }

    @Test
    void withdrawsARequestItsAuthorTakesBack() {
        service.request(request(candidate()));

        Review withdrawn = service.withdraw(REVIEW_ID, AUTHOR).orElseThrow();

        ReviewAssert.assertThat(withdrawn).hasState(Review.State.WITHDRAWN).hasNoPublishedVersion();
    }

    @Test
    void closesTheOffersReviewWhenTheReviewIsRejected() {
        FakeOfferReview offerReview = FakeOfferReview.accepting();
        ReviewService closing = serviceWith(offerReview);
        closing.request(request(candidate()));

        closing.reject(REVIEW_ID, REVIEWER, REASON);

        assertThat(offerReview.closures()).containsExactly(REVIEW_ID);
    }

    @Test
    void closesTheOffersReviewWhenTheReviewIsWithdrawn() {
        FakeOfferReview offerReview = FakeOfferReview.accepting();
        ReviewService closing = serviceWith(offerReview);
        closing.request(request(candidate()));

        closing.withdraw(REVIEW_ID, AUTHOR);

        assertThat(offerReview.closures()).containsExactly(REVIEW_ID);
    }

    @Test
    void leavesTheOffersReviewToTheFreezeWhenTheReviewIsAccepted() {
        FakeOfferReview offerReview = FakeOfferReview.accepting();
        ReviewService accepting = serviceWith(offerReview);
        accepting.request(request(candidate()));

        accepting.decide(REVIEW_ID, REVIEWER);

        assertThat(offerReview.closures()).isEmpty();
    }

    @Test
    void refusesAReviewRequestTheOfferRejectsBeforeItFreezesAnything() {
        RuntimeException refused = new IllegalStateException("the offer refused the review");
        ReviewService refusing = ReviewServiceFixture.service(
                new FakeReviewRepository(), offers, publications, FakeOfferReview.refusing(refused), ReviewFixture.OVERRIDE_ENABLED);

        assertThatThrownBy(() -> refusing.requestFor(OFFER_ID, AUTHOR, List.of())).isSameAs(refused);
        assertThat(offers.reads()).isZero();
        assertThat(reviews).isEmpty();
    }

    @Test
    void asksTheOfferToEnterReviewForTheAttemptItOpens() {
        FakeOfferReview offerReview = FakeOfferReview.accepting();
        ReviewService asking = ReviewServiceFixture.service(
                new FakeReviewRepository(), offers, publications, offerReview, ReviewFixture.OVERRIDE_ENABLED);

        Review requested = asking.requestFor(OFFER_ID, AUTHOR, List.of());

        assertThat(offerReview.asks()).isEqualTo(1);
        assertThat(offerReview.lastAttempt()).isEqualTo(requested.reviewId());
        assertThat(offers.reads()).isEqualTo(1);
    }

    @Test
    void answersAnUnknownReviewWithNothing() {
        assertThat(service.decide(REVIEW_ID, REVIEWER)).isEmpty();
        assertThat(service.reject(REVIEW_ID, REVIEWER, REASON)).isEmpty();
        assertThat(service.withdraw(REVIEW_ID, AUTHOR)).isEmpty();
    }

    @Test
    void passesTheProcessesRefusalThrough() {
        reviews.put(REVIEW_ID, ReviewFixture.rehydrated(aPublishedReview().toReview()));

        ReviewAssert.assertRefused(ReviewException.Error.REVIEW_NOT_OPEN,
                () -> service.reject(REVIEW_ID, REVIEWER, REASON));
        assertThat(publications.publications()).isZero();
    }

    /** A service wired to the offer's port a test can read, over the one review map this test's repositories share. */
    private ReviewService serviceWith(FakeOfferReview offerReview) {
        return ReviewServiceFixture.service(
                new FakeReviewRepository(), offers, publications, offerReview, ReviewFixture.OVERRIDE_ENABLED);
    }

    class FakeReviewRepository implements ReviewRepository {

        @Override
        public Optional<ReviewProcess> get(String reviewId) {
            return Optional.ofNullable(reviews.get(reviewId));
        }

        @Override
        public List<Review> reviewsOf(String offerId) {
            return reviews.values().stream()
                    .map(ReviewProcess::toReview)
                    .filter(review -> review.offerId().equals(offerId))
                    .toList();
        }

        @Override
        public void save(ReviewProcess review) {
            reviews.put(review.reviewId, review);
        }
    }
}
