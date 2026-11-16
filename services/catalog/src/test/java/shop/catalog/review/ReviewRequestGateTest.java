package shop.catalog.review;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import shop.catalog.DomainRefusal;
import shop.catalog.IntegrationTest;
import shop.catalog.offer.Offer;
import shop.catalog.offer.OfferDocumentFixture;
import shop.catalog.offer.OfferFixture;
import shop.catalog.offer.OfferService;
import shop.catalog.quality.QualityFixture;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static shop.catalog.review.ReviewFixture.AUTHOR;
import static shop.catalog.review.ReviewFixture.REVIEWER;

/**
 * 12's `POST /offers/{offerId}/review` driven the way the route drives it — {@link ReviewService#requestFor} over the real offer —
 * so the gate the request has to pass is the offer's own and not a fake's kindness.
 *
 * <p>02 RULE-1 and the removal rule are the price of entering review, and 17 RULE-3 makes the offer state them: an offer without a
 * primary image, or one that is gone, refuses the request in its own vocabulary (D49) before this context freezes anything. A draft
 * that passes is under review when the review exists, because both writes share one transaction (D34, 17 R4).
 */
@IntegrationTest
class ReviewRequestGateTest {

    @Autowired
    ReviewService reviews;
    @Autowired
    OfferService offers;

    @Test
    void refusesAReviewOfAnOfferWithoutAPrimaryImageAndFreezesNothing() {
        String offerId = anOfferUnderEditing();

        assertThatThrownBy(() -> reviews.requestFor(offerId, AUTHOR, List.of()))
                .isInstanceOfSatisfying(DomainRefusal.class,
                        refusal -> assertThat(refusal.reason()).isEqualTo("MEDIA_IMAGE_MISSING"));

        assertThat(reviews.latestReviewOf(offerId)).isEmpty();
        assertThat(stateOf(offerId)).isEqualTo(Offer.State.DRAFT);
    }

    @Test
    void refusesAReviewOfAnOfferThatIsRemoved() {
        String offerId = aRemovedOffer();

        assertThatThrownBy(() -> reviews.requestFor(offerId, AUTHOR, List.of()))
                .isInstanceOfSatisfying(DomainRefusal.class,
                        refusal -> assertThat(refusal.reason()).isEqualTo("OFFER_REMOVED"));

        assertThat(reviews.latestReviewOf(offerId)).isEmpty();
    }

    @Test
    void movesADraftIntoReviewAndFreezesTheContentItHolds() {
        String offerId = anOfferUnderEditing();
        offers.updateMedia(offerId, OfferFixture.mediaOfInstanceA());

        Review requested = reviews.requestFor(offerId, AUTHOR, List.of());

        assertThat(requested.state()).isEqualTo(Review.State.REQUESTED);
        assertThat(requested.candidate().media()).containsExactlyElementsOf(OfferFixture.mediaOfInstanceA());
        assertThat(stateOf(offerId)).isEqualTo(Offer.State.PENDING_REVIEW);
    }

    @Test
    void refusesAReviewOfAnOfferNobodyDrafted() {
        String offerId = ReviewFixture.aNewOfferId();

        assertThatThrownBy(() -> reviews.requestFor(offerId, AUTHOR, List.of()))
                .isInstanceOfSatisfying(DomainRefusal.class,
                        refusal -> assertThat(refusal.reason()).isEqualTo("OFFER_UNKNOWN"));
    }

    private String anOfferUnderEditing() {
        String offerId = ReviewFixture.aNewOfferId();
        offers.draftOffer(offerId, QualityFixture.draftWithIdentifiers(
                OfferDocumentFixture.uniqueProductNumber(), OfferDocumentFixture.uniqueEanGtin()));
        offers.scheduleAvailability(offerId, ReviewCandidateFixture.AVAILABILITY_START);
        return offerId;
    }

    private String aRemovedOffer() {
        String offerId = anOfferUnderEditing();
        offers.updateMedia(offerId, OfferFixture.mediaOfInstanceA());
        offers.publish(offerId, Instant.now(), REVIEWER);
        offers.removeOffer(offerId);
        return offerId;
    }

    private Offer.State stateOf(String offerId) {
        return offers.getOffer(offerId).orElseThrow().state();
    }
}
