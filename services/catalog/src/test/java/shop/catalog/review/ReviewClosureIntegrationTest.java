package shop.catalog.review;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import shop.catalog.IntegrationTest;
import shop.catalog.offer.Offer;
import shop.catalog.offer.OfferCandidate;
import shop.catalog.offer.OfferDocumentFixture;
import shop.catalog.offer.OfferFixture;
import shop.catalog.offer.OfferService;
import shop.catalog.offer.ProductDescriptionDraftFixture;
import shop.catalog.quality.QualityFixture;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static shop.catalog.review.ReviewFixture.AUTHOR;
import static shop.catalog.review.ReviewFixture.REASON;
import static shop.catalog.review.ReviewFixture.REVIEWED_AT;
import static shop.catalog.review.ReviewFixture.REVIEWER;

/**
 * R6 of 17 closed end to end: an attempt that ends without a version takes the offer out of review through the {@code OfferReview}
 * port, in the same transaction as the decision (D34). 04 RULE-5 returns a draft to {@code draft} so the next attempt can start, D37
 * keeps a published offer published, a removed offer keeps its state while the review is still recorded, and a block by 09 ends the
 * attempt as well — nothing an attempt did may leave the offer believing a review is open, or take a live product off the shop.
 */
@IntegrationTest
class ReviewClosureIntegrationTest {

    @Autowired
    ReviewService reviews;
    @Autowired
    OfferService offers;
    @Autowired
    QualityFindings quality;

    @Test
    void aRejectionReturnsTheDraftToDraftAndKeepsTheCandidateForAudit() {
        String offerId = anOfferWithAPrimaryImage();
        Review requested = reviews.requestFor(offerId, AUTHOR, List.of());

        Review rejected = reviews.reject(requested.reviewId(), REVIEWER, REASON).orElseThrow();

        assertThat(rejected.state()).isEqualTo(Review.State.REJECTED);
        assertThat(rejected.reason()).isEqualTo(REASON);
        assertThat(rejected.candidate()).isEqualTo(requested.candidate());
        assertThat(stateOf(offerId)).isEqualTo(Offer.State.DRAFT);
    }

    @Test
    void aRejectedDraftIsRevisableAgain() {
        String offerId = anOfferWithAPrimaryImage();
        Review requested = reviews.requestFor(offerId, AUTHOR, List.of());
        reviews.reject(requested.reviewId(), REVIEWER, REASON);

        Review again = reviews.requestFor(offerId, AUTHOR, List.of());

        assertThat(again.state()).isEqualTo(Review.State.REQUESTED);
        assertThat(again.reviewId()).isNotEqualTo(requested.reviewId());
        assertThat(stateOf(offerId)).isEqualTo(Offer.State.PENDING_REVIEW);
    }

    @Test
    void aWithdrawnDraftIsRevisableAgain() {
        String offerId = anOfferWithAPrimaryImage();
        Review requested = reviews.requestFor(offerId, AUTHOR, List.of());
        reviews.withdraw(requested.reviewId(), AUTHOR);

        assertThat(stateOf(offerId)).isEqualTo(Offer.State.DRAFT);
        assertThat(reviews.requestFor(offerId, AUTHOR, List.of()).state()).isEqualTo(Review.State.REQUESTED);
    }

    @Test
    void aReviewOfARemovedOfferIsStillDecidableAndTheOfferStaysRemoved() {
        String offerId = aPublishedOffer();
        Review requested = reviews.requestFor(offerId, AUTHOR, List.of());
        offers.removeOffer(offerId);

        Review rejected = reviews.reject(requested.reviewId(), REVIEWER, REASON).orElseThrow();

        assertThat(rejected.state()).isEqualTo(Review.State.REJECTED);
        assertThat(stateOf(offerId)).isEqualTo(Offer.State.REMOVED);
    }

    @Test
    void aRejectionOfANextVersionLeavesThePublishedOfferPublishedAndRevertable() {
        String offerId = aPublishedOffer();
        Review requested = reviews.requestFor(offerId, AUTHOR, List.of());

        reviews.reject(requested.reviewId(), REVIEWER, REASON);

        assertThat(stateOf(offerId)).isEqualTo(Offer.State.PUBLISHED);
        assertThat(offers.revert(offerId, 1, REVIEWED_AT, REVIEWER)).isPresent();
        assertThat(offers.getOffer(offerId).orElseThrow().version()).isEqualTo(2);
    }

    @Test
    void aBlockedAcceptanceReturnsTheDraftToDraftSoTheFindingsCanBeFixed() {
        String offerId = anOfferWithAPrimaryImageAndFindings();
        Review requested = reviews.requestFor(offerId, AUTHOR, blockingRulesOf(offerId));

        Review accepted = reviews.decide(requested.reviewId(), REVIEWER).orElseThrow();

        assertThat(accepted.state()).isEqualTo(Review.State.ACCEPTED);
        assertThat(accepted.isPublished()).isFalse();
        assertThat(stateOf(offerId)).isEqualTo(Offer.State.DRAFT);
        assertThat(reviews.requestFor(offerId, AUTHOR, blockingRulesOf(offerId)).state())
                .isEqualTo(Review.State.REQUESTED);
    }

    private String anOfferWithAPrimaryImage() {
        String offerId = ReviewFixture.aNewOfferId();
        offers.draftOffer(offerId, QualityFixture.draftWithIdentifiers(
                OfferDocumentFixture.uniqueProductNumber(), OfferDocumentFixture.uniqueEanGtin()));
        offers.scheduleAvailability(offerId, ReviewCandidateFixture.AVAILABILITY_START);
        offers.updateMedia(offerId, OfferFixture.mediaOfInstanceA());
        return offerId;
    }

    private String aPublishedOffer() {
        String offerId = anOfferWithAPrimaryImage();
        offers.publish(offerId, REVIEWED_AT, REVIEWER);
        return offerId;
    }

    /** A draft 08 finds blocking, so 09 refuses to publish it even when the author pushed it through with an override (RULE-8). */
    private String anOfferWithAPrimaryImageAndFindings() {
        String offerId = ReviewFixture.aNewOfferId();
        offers.draftOffer(offerId, ProductDescriptionDraftFixture.draftWithIdentifiers(
                OfferDocumentFixture.uniqueProductNumber(), OfferDocumentFixture.uniqueEanGtin()));
        offers.scheduleAvailability(offerId, ReviewCandidateFixture.AVAILABILITY_START);
        offers.updateMedia(offerId, OfferFixture.mediaOfInstanceA());
        return offerId;
    }

    /** 04 RULE-8: the rules the request has to name to get past the gate, read from 08's own report on what the offer holds. */
    private List<String> blockingRulesOf(String offerId) {
        OfferCandidate candidate = offers.draftOf(offerId).orElseThrow().candidate();
        return quality.findingsFor(ReviewCandidateFixture.contentWithMedia(candidate.description(), candidate.media()))
                .stream()
                .filter(ReviewFinding::isBlocking)
                .map(ReviewFinding::rule)
                .toList();
    }

    private Offer.State stateOf(String offerId) {
        return offers.getOffer(offerId).orElseThrow().state();
    }
}
