package shop.catalog.mediators;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import shop.catalog.DomainRefusal;
import shop.catalog.IntegrationTest;
import shop.catalog.offer.Offer;
import shop.catalog.offer.OfferDocumentFixture;
import shop.catalog.offer.OfferFixture;
import shop.catalog.offer.OfferService;
import shop.catalog.quality.QualityFixture;
import shop.catalog.review.OfferReview;
import shop.catalog.review.ReviewCandidateFixture;
import shop.catalog.review.ReviewFixture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The {@code OfferReview} edge of 17 through the port that carries it: the review asks, {@link ReviewToOfferMediator} passes the
 * question on, and the offer answers with its own state or its own refusal. The proof is what the shop sees — a state that moved, a
 * reason the client can branch on — not that a call was delegated.
 */
@IntegrationTest
class ReviewToOfferMediatorTest {

    @Autowired
    OfferReview offerReviews;
    @Autowired
    OfferService offers;

    @Test
    void asksTheOfferToEnterReviewSoTheOfferMovesItself() {
        String offerId = aDraftWithAPrimaryImage();

        offerReviews.requestReview(offerId, ReviewFixture.aNewReviewId(), ReviewFixture.REQUESTED_AT);

        assertThat(offers.getOffer(offerId).orElseThrow().state()).isEqualTo(Offer.State.PENDING_REVIEW);
    }

    @Test
    void answersWithTheOffersOwnRefusalWhenTheOfferCannotEnterReview() {
        String offerId = aDraft();

        assertThatThrownBy(() -> offerReviews.requestReview(offerId, ReviewFixture.aNewReviewId(), ReviewFixture.REQUESTED_AT))
                .isInstanceOfSatisfying(DomainRefusal.class,
                        refusal -> assertThat(refusal.reason()).isEqualTo("MEDIA_IMAGE_MISSING"));
        assertThat(offers.getOffer(offerId).orElseThrow().state()).isEqualTo(Offer.State.DRAFT);
    }

    @Test
    void closesTheAttemptSoTheOfferLeavesReviewAndCanBeReviewedAgain() {
        String offerId = aDraftWithAPrimaryImage();
        String reviewId = ReviewFixture.aNewReviewId();
        offerReviews.requestReview(offerId, reviewId, ReviewFixture.REQUESTED_AT);

        offerReviews.closeReview(offerId, reviewId);

        assertThat(offers.getOffer(offerId).orElseThrow().state()).isEqualTo(Offer.State.DRAFT);
        offerReviews.requestReview(offerId, ReviewFixture.aNewReviewId(), ReviewFixture.REQUESTED_AT);
        assertThat(offers.getOffer(offerId).orElseThrow().state()).isEqualTo(Offer.State.PENDING_REVIEW);
    }

    @Test
    void closesTheAttemptOfAPublishedOfferWithoutTakingItOffTheShop() {
        String offerId = aDraftWithAPrimaryImage();
        offers.publish(offerId, ReviewFixture.REVIEWED_AT, ReviewFixture.REVIEWER);
        String reviewId = ReviewFixture.aNewReviewId();
        offerReviews.requestReview(offerId, reviewId, ReviewFixture.REQUESTED_AT);

        offerReviews.closeReview(offerId, reviewId);

        assertThat(offers.getOffer(offerId).orElseThrow().state()).isEqualTo(Offer.State.PUBLISHED);
        assertThat(offers.revert(offerId, 1, ReviewFixture.REVIEWED_AT, ReviewFixture.REVIEWER)).isPresent();
    }

    private String aDraftWithAPrimaryImage() {
        String offerId = aDraft();
        offers.updateMedia(offerId, OfferFixture.mediaOfInstanceA());
        return offerId;
    }

    private String aDraft() {
        String offerId = ReviewFixture.aNewOfferId();
        offers.draftOffer(offerId, QualityFixture.draftWithIdentifiers(
                OfferDocumentFixture.uniqueProductNumber(), OfferDocumentFixture.uniqueEanGtin()));
        offers.scheduleAvailability(offerId, ReviewCandidateFixture.AVAILABILITY_START);
        return offerId;
    }
}
