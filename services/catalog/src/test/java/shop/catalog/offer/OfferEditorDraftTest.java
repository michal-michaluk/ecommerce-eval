package shop.catalog.offer;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import shop.catalog.offer.Offer.State;
import shop.catalog.offer.OfferEvent.OfferDraftUpdated;
import shop.catalog.offer.ProductDescription.Draft;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThatCode;
import static shop.catalog.offer.OfferAssert.assertRefused;
import static shop.catalog.offer.OfferAssert.assertThat;
import static shop.catalog.offer.OfferFixture.AVAILABILITY_START;
import static shop.catalog.offer.OfferFixture.OFFER_ID;
import static shop.catalog.offer.OfferFixture.OTHER_OFFER_ID;
import static shop.catalog.offer.OfferFixture.REVIEWER;
import static shop.catalog.offer.OfferFixture.draftOffer;
import static shop.catalog.offer.OfferFixture.draftOfferWithMedia;
import static shop.catalog.offer.OfferFixture.freeIdentifiers;
import static shop.catalog.offer.OfferFixture.mediaOfInstanceA;
import static shop.catalog.offer.OfferFixture.now;
import static shop.catalog.offer.OfferFixture.today;
import static shop.catalog.offer.ProductDescriptionDraftFixture.draftOfInstanceA;
import static shop.catalog.offer.ProductDescriptionDraftFixture.draftWithIdentifiers;
import static shop.catalog.offer.ProductDescriptionDraftFixture.draftWithTitle;
import static shop.catalog.offer.ProductDescriptionDraftFixture.draftWithoutEanGtin;
import static shop.catalog.offer.ProductDescriptionDraftFixture.draftWithoutIdentifiers;
import static shop.catalog.offer.ProductDescriptionFixture.INSTANCE_A_EAN;
import static shop.catalog.offer.ProductDescriptionFixture.INSTANCE_A_TITLE;
import static shop.catalog.offer.ProductDescriptionFixture.descriptionOfInstanceA;
import static shop.catalog.offer.ProductDescriptionFixture.identifiersOfInstanceB;

/** The draft surface of the offer editor: RULE-3, RULE-7, RULE-8, RULE-9 and the population rule RULE-11. */
class OfferEditorDraftTest {

    @Test
    void newOfferIsADraftWithoutAnyVersion() {
        assertThat(draftOffer())
                .hasOfferId(OFFER_ID)
                .hasState(State.DRAFT)
                .hasVersion(0)
                .hasNoAvailabilityStart();
    }

    @Test
    void newOfferRecordsTheFirstDraftFact() {
        Assertions.assertThat(draftOffer().events).containsExactly(new OfferDraftUpdated(OFFER_ID));
    }

    @Test
    void editProducesANewDraftInsteadOfMutatingTheOldOne() {
        Draft original = draftOfInstanceA();
        OfferEditor offer = OfferEditor.draftAnOffer(OFFER_ID, original, freeIdentifiers());

        offer.updateDescription(draftWithTitle("LEGO Star Wars X-Wing 75423"), freeIdentifiers());

        Assertions.assertThat(original.title()).isEqualTo(INSTANCE_A_TITLE);
        Assertions.assertThat(offer.events).hasSize(2);
    }

    @Test
    void editWithNoChangeEmitsNothing() {
        OfferEditor offer = draftOffer();
        offer.events.clear();

        offer.updateDescription(draftOfInstanceA(), freeIdentifiers());

        Assertions.assertThat(offer.events).isEmpty();
    }

    @Test
    void everyAcceptedDraftChangeIsRecorded() {
        OfferEditor offer = draftOffer();
        offer.events.clear();

        offer.updateDescription(draftWithTitle("LEGO Star Wars X-Wing 75423"), freeIdentifiers());
        offer.updateMedia(mediaOfInstanceA());
        offer.scheduleAvailability(AVAILABILITY_START);

        Assertions.assertThat(offer.events).containsExactly(
                new OfferDraftUpdated(OFFER_ID), new OfferDraftUpdated(OFFER_ID), new OfferDraftUpdated(OFFER_ID));
    }

    @Test
    void draftAcceptsAViolationThatTheFreezeRefuses() {
        OfferEditor offer = draftOfferWithMedia();

        assertThatCode(() -> offer.updateDescription(draftWithoutEanGtin(), freeIdentifiers()))
                .doesNotThrowAnyException();
        assertRefused(OfferException.Error.EAN_GTIN_INVALID, () -> offer.publish(now(), REVIEWER, freeIdentifiers()));
        assertThat(offer).hasState(State.DRAFT).hasVersion(0);
    }

    @Test
    void draftStaysEditableWithoutAnyIdentifiers() {
        OfferEditor offer = draftOffer();

        offer.updateDescription(draftWithoutIdentifiers(), freeIdentifiers());

        assertThat(offer).hasState(State.DRAFT);
    }

    @Test
    void availabilityMayLieInTheFuture() {
        OfferEditor offer = draftOfferWithMedia();
        LocalDate inSixMonths = today().plusMonths(6);

        offer.scheduleAvailability(inSixMonths);

        assertThat(offer).hasAvailabilityStart(inSixMonths).hasState(State.DRAFT);
    }

    @Test
    void availabilityWithoutADateIsRefused() {
        OfferEditor offer = draftOffer();

        assertRefused(OfferException.Error.COMMAND_VALUE_MISSING, () -> offer.scheduleAvailability(null));
    }

    @Test
    void mediaWithoutAListIsRefused() {
        OfferEditor offer = draftOffer();

        assertRefused(OfferException.Error.COMMAND_VALUE_MISSING, () -> offer.updateMedia(null));
    }

    @Test
    void newOfferIsRefusedWhenAnotherOfferCarriesTheProductNumber() {
        FakeOfferRegistry offers = freeIdentifiers().withProductNumber("75423", OTHER_OFFER_ID);

        assertRefused(OfferException.Error.PRODUCT_NUMBER_TAKEN,
                () -> OfferEditor.draftAnOffer(OFFER_ID, draftOfInstanceA(), offers));
    }

    @Test
    void newOfferIsRefusedWhenAnotherOfferCarriesTheEanGtin() {
        FakeOfferRegistry offers = freeIdentifiers().withEanGtin(INSTANCE_A_EAN, OTHER_OFFER_ID);

        assertRefused(OfferException.Error.EAN_GTIN_TAKEN,
                () -> OfferEditor.draftAnOffer(OFFER_ID, draftOfInstanceA(), offers));
    }

    @Test
    void offerKeepsItsOwnIdentifiers() {
        FakeOfferRegistry offers = freeIdentifiers()
                .withProductNumber("75423", OFFER_ID)
                .withEanGtin(INSTANCE_A_EAN, OFFER_ID);

        OfferEditor offer = OfferEditor.draftAnOffer(OFFER_ID, draftOfInstanceA(), offers);

        assertThat(offer).hasState(State.DRAFT);
    }

    @Test
    void refusedIdentifierEditLeavesTheDraftAsItWas() {
        OfferEditor offer = draftOfferWithMedia();
        FakeOfferRegistry offers = freeIdentifiers().withProductNumber("75375", OTHER_OFFER_ID);

        assertRefused(OfferException.Error.PRODUCT_NUMBER_TAKEN,
                () -> offer.updateDescription(draftWithIdentifiers(identifiersOfInstanceB()), offers));
        offer.publish(now(), REVIEWER, freeIdentifiers());

        Assertions.assertThat(offer.version(1).description()).isEqualTo(descriptionOfInstanceA());
    }

    @Test
    void identifierTakenAfterTheEditIsRefusedAtTheFreeze() {
        OfferEditor offer = draftOfferWithMedia();
        FakeOfferRegistry takenMeanwhile = freeIdentifiers().withProductNumber("75423", OTHER_OFFER_ID);

        assertRefused(OfferException.Error.PRODUCT_NUMBER_TAKEN,
                () -> offer.publish(now(), REVIEWER, takenMeanwhile));
        assertThat(offer).hasState(State.DRAFT).hasVersion(0);
    }
}
