package shop.catalog.reads;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import shop.catalog.IntegrationTest;
import shop.catalog.offer.Offer;
import shop.catalog.offer.OfferFixture;
import shop.catalog.offer.OfferService;
import shop.catalog.offer.ProductDescriptionDraftFixture;
import shop.catalog.review.ReviewFixture;

import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static shop.catalog.reads.OfferVisibility.Outcome.HIDDEN;
import static shop.catalog.reads.OfferVisibility.Outcome.VISIBLE;

/**
 * What the offer context and the read side answer <em>together</em> when a live offer is reviewed again — the D37 case of 11, driven
 * through the producer's own port instead of a hand-built state.
 *
 * <p>D37 keeps a {@code published} offer {@code published} while its next version is reviewed: {@code OfferEditor.requestReview} only
 * moves a {@code draft} to {@code pendingReview}, so the shop keeps serving the version in force. The read side adds no rule of its
 * own — it answers with the state the offer holds (17 RULE-6) — and R3 of 11 therefore never hides a live product behind an open
 * review.
 */
@IntegrationTest
class ReadsLiveOfferUnderReviewTest {

    @Autowired
    OfferService offers;
    @Autowired
    OfferDraftReadsProjection drafts;

    @Test
    void keepsALiveOfferVisibleWhileItsNextVersionIsReviewed() {
        String offerId = aPublishedOfferAvailableSinceYesterday();
        assertThat(draftOf(offerId).visibility()).isEqualTo(VISIBLE);

        offers.requestReview(offerId);

        OfferDraftReads reReviewed = draftOf(offerId);
        assertThat(reReviewed.state()).isEqualTo(Offer.State.PUBLISHED);
        assertThat(reReviewed.visibility()).isEqualTo(VISIBLE);
    }

    private String aPublishedOfferAvailableSinceYesterday() {
        String offerId = ReviewFixture.aNewOfferId();
        offers.draftOffer(offerId, ProductDescriptionDraftFixture.draftOfInstanceA());
        offers.updateMedia(offerId, OfferFixture.mediaOfInstanceA());
        offers.scheduleAvailability(offerId, LocalDate.now().minusDays(2));
        offers.publish(offerId, Instant.now(), OfferFixture.REVIEWER);
        return offerId;
    }

    private OfferDraftReads draftOf(String offerId) {
        return drafts.findDraft(offerId).orElseThrow();
    }
}
