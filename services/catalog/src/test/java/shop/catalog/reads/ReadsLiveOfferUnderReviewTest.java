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
 * <p><b>This test pins a gap, it does not endorse it.</b> 02's lifecycle and the recommendation in {@code spec.md} (Open Topics,
 * D37) say the offer stays {@code published} while its candidate is reviewed, so the shop keeps serving the live version. The
 * aggregate does not do that: {@code OfferEditor.requestReview} sets {@code PENDING_REVIEW} whichever state the offer was in, which
 * {@code OfferEditorLifecycleTest.publishedOfferCanBeReviewedAgain} pins as intended, and 09 R1 does not list {@code published} as
 * an allowed state yet. R3 of 11 then hides the live offer, and this context must not compensate: it would need the offer's version
 * history to tell "re-reviewed" from "never published", which is a second copy of a foreign fact and a second place the visibility
 * rule could drift (17 RULE-6).
 *
 * <p>Flip the two expectations of this test in the commit that gives 02 the D37 behaviour.
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
