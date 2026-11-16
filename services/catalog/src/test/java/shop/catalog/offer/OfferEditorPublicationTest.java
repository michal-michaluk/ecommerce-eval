package shop.catalog.offer;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import shop.catalog.offer.Offer.State;
import shop.catalog.offer.ProductDescription.DescriptionBlock;

import java.util.List;

import static shop.catalog.offer.OfferAssert.assertRefused;
import static shop.catalog.offer.OfferAssert.assertThat;
import static shop.catalog.offer.OfferFixture.AVAILABILITY_START;
import static shop.catalog.offer.OfferFixture.REVIEWER;
import static shop.catalog.offer.OfferFixture.aDifferentDraft;
import static shop.catalog.offer.OfferFixture.draftOfferWithMediaOnly;
import static shop.catalog.offer.OfferFixture.freeIdentifiers;
import static shop.catalog.offer.OfferFixture.mediaOfInstanceA;
import static shop.catalog.offer.OfferFixture.mediaWithoutImage;
import static shop.catalog.offer.OfferFixture.now;
import static shop.catalog.offer.OfferFixture.offerPendingReview;
import static shop.catalog.offer.OfferFixture.publishedOffer;
import static shop.catalog.offer.OfferFixture.versionFrozen;
import static shop.catalog.offer.OfferFixture.versionReverted;
import static shop.catalog.offer.OfferFixture.versionWithNumber;
import static shop.catalog.offer.ProductDescriptionFixture.descriptionOfInstanceA;

/** Freezing a version (RULE-1, RULE-3, RULE-4, RULE-8, RULE-10) and reverting to one (RULE-5). */
class OfferEditorPublicationTest {

    @Test
    void publicationFreezesTheDescriptionByValue() {
        OfferEditor offer = offerPendingReview();

        offer.publish(now(), REVIEWER, freeIdentifiers());

        Assertions.assertThat(offer.version(1).description()).isEqualTo(descriptionOfInstanceA());
        Assertions.assertThat(offer.version(1).number()).isEqualTo(1);
    }

    @Test
    void publicationFreezesTheMediaAndTheAvailabilityDate() {
        OfferEditor offer = offerPendingReview();

        offer.publish(now(), REVIEWER, freeIdentifiers());

        Assertions.assertThat(offer.version(1).media()).isEqualTo(mediaOfInstanceA());
        assertThat(offer).hasAvailabilityStart(AVAILABILITY_START).hasState(State.PUBLISHED);
    }

    @Test
    void publicationRecordsTheReviewerAndTheMoment() {
        OfferEditor offer = offerPendingReview();

        offer.publish(now(), REVIEWER, freeIdentifiers());

        Assertions.assertThat(offer.version(1).publishedAt()).isEqualTo(now());
        Assertions.assertThat(offer.version(1).publishedBy()).isEqualTo(REVIEWER);
        Assertions.assertThat(offer.version(1).copiedFrom()).isNull();
    }

    @Test
    void freezeIsRecordedWithTheFrozenValue() {
        OfferEditor offer = offerPendingReview();
        offer.events.clear();

        offer.publish(now(), REVIEWER, freeIdentifiers());

        Assertions.assertThat(offer.events).containsExactly(versionFrozen(1, descriptionOfInstanceA()));
    }

    @Test
    void editAfterPublicationCannotChangeTheFrozenVersion() {
        OfferEditor offer = publishedOffer();

        offer.updateDescription(aDifferentDraft(), freeIdentifiers());

        Assertions.assertThat(offer.version(1).description()).isEqualTo(descriptionOfInstanceA());
    }

    @Test
    void theNextReviewPublishesTheNextVersion() {
        OfferEditor offer = publishedOffer();
        offer.updateDescription(aDifferentDraft(), freeIdentifiers());

        offer.publish(now(), REVIEWER, freeIdentifiers());

        assertThat(offer).hasVersion(2);
        Assertions.assertThat(offer.version(2).description().blocks())
                .containsExactly(new DescriptionBlock.Paragraph("Wersja druga opisu."));
    }

    @Test
    void publicationOfAViolatedDraftIsRefused() {
        OfferEditor offer = offerPendingReview();
        offer.updateDescription(ProductDescriptionDraftFixture.draftWithoutEanGtin(), freeIdentifiers());

        assertRefused(OfferException.Error.EAN_GTIN_INVALID, () -> offer.publish(now(), REVIEWER, freeIdentifiers()));
        assertThat(offer).hasState(State.PENDING_REVIEW).hasVersion(0);
    }

    @Test
    void publicationWithoutAnyImageIsRefused() {
        OfferEditor offer = draftOfferWithMediaOnly();
        offer.updateMedia(mediaWithoutImage());

        assertRefused(OfferException.Error.MEDIA_IMAGE_MISSING, () -> offer.publish(now(), REVIEWER, freeIdentifiers()));
        assertThat(offer).hasVersion(0);
    }

    @Test
    void publicationWithoutAnAvailabilityDateIsRefused() {
        OfferEditor offer = draftOfferWithMediaOnly();

        assertRefused(OfferException.Error.AVAILABILITY_START_MISSING,
                () -> offer.publish(now(), REVIEWER, freeIdentifiers()));
        assertThat(offer).hasVersion(0).hasNoAvailabilityStart();
    }

    @Test
    void publicationWithoutAMomentIsRefused() {
        OfferEditor offer = offerPendingReview();

        assertRefused(OfferException.Error.COMMAND_VALUE_MISSING, () -> offer.publish(null, REVIEWER, freeIdentifiers()));
    }

    @Test
    void publicationWithoutAReviewerIsRefused() {
        OfferEditor offer = offerPendingReview();

        assertRefused(OfferException.Error.COMMAND_VALUE_MISSING, () -> offer.publish(now(), null, freeIdentifiers()));
    }

    @Test
    void revertCopiesTheContentOfAnOlderVersion() {
        OfferEditor offer = publishedOffer();
        offer.updateDescription(aDifferentDraft(), freeIdentifiers());
        offer.publish(now(), REVIEWER, freeIdentifiers());

        offer.revert(1, now(), REVIEWER, freeIdentifiers());

        assertThat(offer).hasState(State.PUBLISHED).hasVersion(3);
        Assertions.assertThat(offer.version(3).description()).isEqualTo(descriptionOfInstanceA());
        Assertions.assertThat(offer.version(3).copiedFrom()).isEqualTo(1);
    }

    @Test
    void revertNeverReusesAVersionNumber() {
        OfferEditor offer = publishedOffer();
        offer.updateDescription(aDifferentDraft(), freeIdentifiers());
        offer.publish(now(), REVIEWER, freeIdentifiers());

        offer.revert(1, now(), REVIEWER, freeIdentifiers());

        Assertions.assertThat(List.of(offer.version(1), offer.version(2), offer.version(3)))
                .extracting(OfferVersion::number)
                .containsExactly(1, 2, 3);
        Assertions.assertThat(offer.version(2).description().blocks())
                .containsExactly(new DescriptionBlock.Paragraph("Wersja druga opisu."));
    }

    @Test
    void revertKeepsTheAuthorCurrentDraft() {
        OfferEditor offer = publishedOffer();
        offer.updateDescription(aDifferentDraft(), freeIdentifiers());
        offer.publish(now(), REVIEWER, freeIdentifiers());

        offer.revert(1, now(), REVIEWER, freeIdentifiers());
        offer.publish(now(), REVIEWER, freeIdentifiers());

        Assertions.assertThat(offer.version(4).description().blocks())
                .containsExactly(new DescriptionBlock.Paragraph("Wersja druga opisu."));
    }

    @Test
    void revertWhileAReviewIsOpenIsRefused() {
        OfferEditor offer = publishedOffer();
        offer.updateDescription(aDifferentDraft(), freeIdentifiers());
        offer.requestReview();

        assertRefused(OfferException.Error.REVIEW_OPEN, () -> offer.revert(1, now(), REVIEWER, freeIdentifiers()));
    }

    @Test
    void revertIsRecordedWithItsSource() {
        OfferEditor offer = publishedOffer();
        offer.events.clear();

        offer.revert(1, now(), REVIEWER, freeIdentifiers());

        Assertions.assertThat(offer.events).containsExactly(versionReverted(2, 1));
    }

    @Test
    void revertOfAVersionThatDoesNotExistIsRefused() {
        OfferEditor offer = publishedOffer();

        assertRefused(OfferException.Error.OFFER_VERSION_MISSING,
                () -> offer.revert(7, now(), REVIEWER, freeIdentifiers()));
    }

    @Test
    void revertWithoutAReviewerIsRefused() {
        OfferEditor offer = publishedOffer();

        assertRefused(OfferException.Error.COMMAND_VALUE_MISSING, () -> offer.revert(1, now(), null, freeIdentifiers()));
    }

    @Test
    void aVersionNumberBelowOneIsRefused() {
        assertRefused(OfferException.Error.OFFER_VERSION_NUMBER_INVALID, () -> versionWithNumber(0));
    }
}
