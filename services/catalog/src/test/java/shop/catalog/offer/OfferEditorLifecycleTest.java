package shop.catalog.offer;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import shop.catalog.offer.Offer.State;
import shop.catalog.offer.OfferEvent.OfferReviewRequested;

import static shop.catalog.offer.OfferAssert.assertRefused;
import static shop.catalog.offer.OfferAssert.assertThat;
import static shop.catalog.offer.OfferFixture.AVAILABILITY_START;
import static shop.catalog.offer.OfferFixture.OFFER_ID;
import static shop.catalog.offer.OfferFixture.REVIEWER;
import static shop.catalog.offer.OfferFixture.aDifferentDraft;
import static shop.catalog.offer.OfferFixture.draftOffer;
import static shop.catalog.offer.OfferFixture.draftOfferWithMedia;
import static shop.catalog.offer.OfferFixture.freeIdentifiers;
import static shop.catalog.offer.OfferFixture.mediaWithPositionGap;
import static shop.catalog.offer.OfferFixture.mediaWithPrimaryVideo;
import static shop.catalog.offer.OfferFixture.mediaWithTwoPrimaries;
import static shop.catalog.offer.OfferFixture.mediaWithTwoVideos;
import static shop.catalog.offer.OfferFixture.mediaWithoutImage;
import static shop.catalog.offer.OfferFixture.mediaWithoutPrimary;
import static shop.catalog.offer.OfferFixture.now;
import static shop.catalog.offer.OfferFixture.offerPendingReview;
import static shop.catalog.offer.OfferFixture.publishedOffer;
import static shop.catalog.offer.OfferFixture.removedOffer;

/** The 02 lifecycle: draft → pendingReview → published → removed, the media gate of RULE-1 and RULE-2, and the final state of RULE-6. */
class OfferEditorLifecycleTest {

    @Test
    void requestReviewMovesTheDraftIntoReview() {
        OfferEditor offer = draftOfferWithMedia();

        offer.requestReview();

        assertThat(offer).hasState(State.PENDING_REVIEW).hasVersion(0);
    }

    @Test
    void requestReviewIsRecordedOnce() {
        OfferEditor offer = draftOfferWithMedia();
        offer.events.clear();

        offer.requestReview();
        offer.requestReview();

        Assertions.assertThat(offer.events).containsExactly(new OfferReviewRequested(OFFER_ID));
    }

    @Test
    void requestReviewWithoutAnyImageIsRefused() {
        OfferEditor offer = draftOffer();
        offer.updateMedia(mediaWithoutImage());

        assertRefused(OfferException.Error.MEDIA_IMAGE_MISSING, offer::requestReview);
        assertThat(offer).hasState(State.DRAFT);
    }

    @Test
    void requestReviewWithoutAnyPrimaryIsRefused() {
        OfferEditor offer = draftOffer();
        offer.updateMedia(mediaWithoutPrimary());

        assertRefused(OfferException.Error.MEDIA_PRIMARY_AMBIGUOUS, offer::requestReview);
    }

    @Test
    void requestReviewWithTwoPrimariesIsRefused() {
        OfferEditor offer = draftOffer();
        offer.updateMedia(mediaWithTwoPrimaries());

        assertRefused(OfferException.Error.MEDIA_PRIMARY_AMBIGUOUS, offer::requestReview);
    }

    @Test
    void requestReviewWithAPrimaryVideoIsRefused() {
        OfferEditor offer = draftOffer();
        offer.updateMedia(mediaWithPrimaryVideo());

        assertRefused(OfferException.Error.MEDIA_PRIMARY_AMBIGUOUS, offer::requestReview);
    }

    @Test
    void requestReviewWithAPositionGapIsRefused() {
        OfferEditor offer = draftOffer();
        offer.updateMedia(mediaWithPositionGap());

        assertRefused(OfferException.Error.MEDIA_POSITIONS_NOT_CONTIGUOUS, offer::requestReview);
    }

    @Test
    void requestReviewWithTwoVideosIsRefused() {
        OfferEditor offer = draftOffer();
        offer.updateMedia(mediaWithTwoVideos());

        assertRefused(OfferException.Error.MEDIA_VIDEO_COUNT, offer::requestReview);
    }

    @Test
    void publishMovesTheReviewedOfferToPublished() {
        OfferEditor offer = offerPendingReview();

        offer.publish(now(), REVIEWER, freeIdentifiers());

        assertThat(offer).hasState(State.PUBLISHED).hasVersion(1);
    }

    @Test
    void publishAcceptsADraftBecauseTheDecisionAllowsIt() {
        OfferEditor offer = draftOfferWithMedia();

        offer.publish(now(), REVIEWER, freeIdentifiers());

        assertThat(offer).hasState(State.PUBLISHED).hasVersion(1);
    }

    @Test
    void publishIsRefusedWhileTheOfferIsRemoved() {
        OfferEditor offer = removedOffer();

        assertRefused(OfferException.Error.OFFER_REMOVED, () -> offer.publish(now(), REVIEWER, freeIdentifiers()));
    }

    @Test
    void publishedOfferCanBeReviewedAgainWithoutLeavingTheShop() {
        OfferEditor offer = publishedOffer();

        offer.requestReview();

        assertThat(offer).hasState(State.PUBLISHED).hasVersion(1);
    }

    @Test
    void removalOfAPublishedOfferWithdrawsTheProduct() {
        OfferEditor offer = publishedOffer();

        offer.remove();

        assertThat(offer).hasState(State.REMOVED).hasVersion(1);
    }

    @Test
    void removalOfADraftOfferIsRefused() {
        OfferEditor offer = draftOffer();

        assertRefused(OfferException.Error.REMOVAL_NEEDS_PUBLICATION, offer::remove);
        assertThat(offer).hasState(State.DRAFT);
    }

    @Test
    void removalOfAnOfferUnderReviewIsRefused() {
        OfferEditor offer = offerPendingReview();

        assertRefused(OfferException.Error.REMOVAL_NEEDS_PUBLICATION, offer::remove);
        assertThat(offer).hasState(State.PENDING_REVIEW);
    }

    @Test
    void secondRemovalIsRefused() {
        OfferEditor offer = removedOffer();

        assertRefused(OfferException.Error.REMOVAL_NEEDS_PUBLICATION, offer::remove);
    }

    @Test
    void editAfterRemovalIsRefused() {
        OfferEditor offer = removedOffer();

        assertRefused(OfferException.Error.OFFER_REMOVED,
                () -> offer.updateDescription(aDifferentDraft(), freeIdentifiers()));
    }

    @Test
    void mediaEditAfterRemovalIsRefused() {
        OfferEditor offer = removedOffer();

        assertRefused(OfferException.Error.OFFER_REMOVED, () -> offer.updateMedia(mediaWithoutImage()));
    }

    @Test
    void availabilityEditAfterRemovalIsRefused() {
        OfferEditor offer = removedOffer();

        assertRefused(OfferException.Error.OFFER_REMOVED, () -> offer.scheduleAvailability(AVAILABILITY_START));
    }

    @Test
    void requestReviewAfterRemovalIsRefused() {
        OfferEditor offer = removedOffer();

        assertRefused(OfferException.Error.OFFER_REMOVED, offer::requestReview);
    }

    @Test
    void revertAfterRemovalIsRefused() {
        OfferEditor offer = removedOffer();

        assertRefused(OfferException.Error.OFFER_REMOVED, () -> offer.revert(1, now(), REVIEWER, freeIdentifiers()));
    }
}
