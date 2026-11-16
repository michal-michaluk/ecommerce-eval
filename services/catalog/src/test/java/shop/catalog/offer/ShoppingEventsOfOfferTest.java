package shop.catalog.offer;

import org.junit.jupiter.api.Test;
import shop.catalog.JsonAssert;
import shop.catalog.PublishedEvents;
import shop.catalog.tools.EventEnvelope;
import shop.catalog.tools.EventStream;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

/**
 * Element 13, the offer half: S1 (a publication becomes one snapshot), S6 (a removal), S7 (a revert is the next version of the older
 * content), S9 (a draft edit ships nothing) and S8 as far as the producer owns it — every publication gets its own {@code eventId} while
 * equal content keeps its {@code descriptionHash}, which is what a consumer needs to deduplicate (RULE-5). The payload is compared with
 * the schema of 13 field by field, and the hash is recomputed the way a consumer would, from the payload it received (RULE-10, D39).
 */
class ShoppingEventsOfOfferTest {

    final ShoppingEventsFixture.Offers offers = new ShoppingEventsFixture.Offers();
    final EventStream stream = mock(EventStream.class);
    final OfferIntegration integration = ShoppingEventsFixture.integrationOf(offers, stream);

    @Test
    void publishesTheFrozenVersionAsOneSnapshotWithEveryComponentOfTheValue() {
        OfferEditor offer = ShoppingEventsFixture.publishedOffer();
        offers.save(offer);

        integration.handle(OfferFixture.versionFrozen(1, offer.version(1).description()));

        EventEnvelope envelope = PublishedEvents.only(PublishedEvents.of(stream));
        assertThat(envelope.eventType()).isEqualTo(OfferPublished.TYPE);
        assertThat(envelope.eventVersion()).isEqualTo(EventEnvelope.VERSION);
        assertThat(envelope.key()).isEqualTo(OfferFixture.OFFER_ID);
        assertThat(envelope.occurredAt()).isEqualTo(OfferFixture.PUBLISHED_AT);
        OfferPublished shipped = (OfferPublished) envelope.payload();
        assertThat(shipped.descriptionHash()).isEqualTo(ShoppingEventsFixture.hashOfShippedPayload(shipped));
        JsonAssert.assertThat(shipped).isExactlyLike(
                ShoppingEventsFixture.publishedJson(1, ShoppingEventsFixture.hashOfInstanceA()));
    }

    @Test
    void publishesOnlyTheThreeFactsTheShopIsEntitledTo() {
        assertThat(PublishedEvents.factsHandledBy(OfferIntegration.class))
                .containsExactlyInAnyOrder(
                        OfferEvent.OfferVersionFrozen.class,
                        OfferEvent.OfferVersionReverted.class,
                        OfferEvent.OfferRemoved.class);
    }

    @Test
    void publishesIdentityAndVersionOnlyForAConsumerThatPrefersToPull() {
        OfferEditor offer = ShoppingEventsFixture.publishedOffer();
        offers.save(offer);
        OfferIntegration thin = ShoppingEventsFixture.thinIntegrationOf(offers, stream);

        thin.handle(OfferFixture.versionFrozen(1, offer.version(1).description()));

        JsonAssert.assertThat(PublishedEvents.only(PublishedEvents.of(stream)).payload())
                .isExactlyLike(ShoppingEventsFixture.thinJson());
    }

    @Test
    void publishesTheNextVersionWithTheOlderContentOnARevert() {
        OfferEditor offer = ShoppingEventsFixture.offerAtVersionThree();
        offers.save(offer);
        offer.revert(1, OfferFixture.now(), OfferFixture.REVIEWER, OfferFixture.freeIdentifiers());
        offers.save(offer);

        integration.handle(OfferFixture.versionReverted(4, 1));

        JsonAssert.assertThat(PublishedEvents.only(PublishedEvents.of(stream)).payload())
                .isExactlyLike(ShoppingEventsFixture.publishedJson(4, ShoppingEventsFixture.hashOfInstanceA()));
    }

    @Test
    void publishesAReplayOfOnePublicationAsItsOwnEnvelopeOverTheSameContent() {
        OfferEditor offer = ShoppingEventsFixture.publishedOffer();
        offers.save(offer);
        OfferEvent.OfferVersionFrozen frozen = OfferFixture.versionFrozen(1, offer.version(1).description());

        integration.handle(frozen);
        integration.handle(frozen);

        List<EventEnvelope> published = PublishedEvents.of(stream);
        assertThat(published).hasSize(2);
        assertThat(published.getFirst().eventId()).isNotEqualTo(published.getLast().eventId());
        assertThat(published.getFirst().payload()).isEqualTo(published.getLast().payload());
    }

    @Test
    void publishesNothingWhenTheDraftIsEdited() {
        OfferEditor offer = ShoppingEventsFixture.publishedOffer();

        offer.updateDescription(OfferFixture.aDifferentDraft(), OfferFixture.freeIdentifiers());

        assertThat(DocumentOfferRepository.eventsEmittedFrom(offer))
                .containsExactly(new OfferEvent.OfferDraftUpdated(OfferFixture.OFFER_ID));
        assertThat(PublishedEvents.of(stream)).isEmpty();
    }

    @Test
    void publishesProductRemovedWithTheInstantTheRemovalBecamePublic() {
        OfferEditor offer = ShoppingEventsFixture.removedOffer();
        offers.save(offer);

        integration.handle(new OfferEvent.OfferRemoved(OfferFixture.OFFER_ID));

        EventEnvelope envelope = PublishedEvents.only(PublishedEvents.of(stream));
        assertThat(envelope.eventType()).isEqualTo(ProductRemoved.TYPE);
        assertThat(envelope.key()).isEqualTo(OfferFixture.OFFER_ID);
        JsonAssert.assertThat(envelope.payload()).isExactlyLike(ShoppingEventsFixture.removedJson(OfferFixture.PUBLISHED_AT));
    }

    @Test
    void refusesToShipWhenTheBrokerRefuses() {
        OfferEditor offer = ShoppingEventsFixture.publishedOffer();
        offers.save(offer);
        doThrow(new IllegalStateException("broker refused")).when(stream).publish(any());

        assertThatThrownBy(() -> integration.handle(OfferFixture.versionFrozen(1, offer.version(1).description())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("broker refused");
    }

    @Test
    void hashesEqualContentEquallyAndEveryChangedComponentDifferently() {
        String instanceA = ShoppingEventsFixture.hashOfInstanceA();
        String sameContent = ShoppingEventsFixture.hashOf(ProductDescriptionFixture.INSTANCE_A_TITLE,
                OfferFixture.mediaOfInstanceA(), OfferFixture.AVAILABILITY_START);

        assertThat(instanceA).matches("sha256:[0-9a-f]{64}");
        assertThat(instanceA).isEqualTo("sha256:919e2b9ffb5cfb5b32d3bc05340eec5d9f002cf3b27585fc9edce95f8503c332");
        assertThat(sameContent).isEqualTo(instanceA);
        assertThat(ShoppingEventsFixture.hashOf(ShoppingEventsFixture.EDITED_TITLE,
                OfferFixture.mediaOfInstanceA(), OfferFixture.AVAILABILITY_START)).isNotEqualTo(instanceA);
        assertThat(ShoppingEventsFixture.hashOf(ProductDescriptionFixture.INSTANCE_A_TITLE,
                List.of(OfferFixture.image("M-01", 1, true)), OfferFixture.AVAILABILITY_START)).isNotEqualTo(instanceA);
        assertThat(ShoppingEventsFixture.hashOf(ProductDescriptionFixture.INSTANCE_A_TITLE,
                OfferFixture.mediaOfInstanceA(), OfferFixture.AVAILABILITY_START.plusDays(1))).isNotEqualTo(instanceA);
    }
}
