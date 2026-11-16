package shop.catalog.pricing;

import org.junit.jupiter.api.Test;
import shop.catalog.JsonAssert;
import shop.catalog.PublishedEvents;
import shop.catalog.tools.EventEnvelope;
import shop.catalog.tools.EventStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Element 13, the pricing half: scheduling is silent (S2, S4) and activation is the only trigger (S3, S5), with the payload of 13
 * compared field by field. A published price is the entry the offer has <em>open</em> when the poll runs, so a catch-up that also closed an
 * older entry ships the live one and a currency with nothing open ships nothing at all (RULE-7, RULE-8).
 */
class ShoppingEventsOfPricingTest {

    final PricesChangedFixture.Models models = new PricesChangedFixture.Models();
    final EventStream stream = mock(EventStream.class);
    final PricesIntegration integration = PricesChangedFixture.integrationOf(models, stream);

    @Test
    void publishesOnePricesChangedWhenAScheduledPriceBecomesActive() {
        PricingModelEditor pricing = PricesChangedFixture.modelWithAPriceChangeAndAPercentageDiscount();
        models.save(pricing);
        PricingEvent.PriceActivated fact = PricesChangedFixture.priceActivated(
                PricesChangedFixture.activationFacts(pricing, PricingFixture.JULY_1));

        integration.handle(fact);

        EventEnvelope envelope = PublishedEvents.only(PublishedEvents.of(stream));
        assertThat(envelope.eventType()).isEqualTo(PricesChanged.TYPE);
        assertThat(envelope.key()).isEqualTo(PricingFixture.OFFER_ID);
        assertThat(envelope.occurredAt()).isEqualTo(PricingFixture.JULY_1);
        JsonAssert.assertThat(envelope.payload()).isExactlyLike(PricesChangedFixture.priceActivationJson());
    }

    @Test
    void publishesOnePricesChangedWhenAScheduledDiscountBecomesActive() {
        PricingModelEditor pricing = PricesChangedFixture.modelWithAPriceChangeAndAPercentageDiscount();
        models.save(pricing);
        PricingEvent.DiscountActivated fact = PricesChangedFixture.discountActivated(
                PricesChangedFixture.activationFacts(pricing, PricingFixture.JULY_1));

        integration.handle(fact);

        JsonAssert.assertThat(PublishedEvents.only(PublishedEvents.of(stream)).payload())
                .isExactlyLike(PricesChangedFixture.discountActivationJson());
    }

    @Test
    void publishesAPriceChangeWithoutDiscountsWhenNoDiscountIsActive() {
        PricingModelEditor pricing = PricesChangedFixture.modelWithAPriceChangeAndNoDiscount();
        models.save(pricing);
        PricingEvent.PriceActivated fact = PricesChangedFixture.priceActivated(
                PricesChangedFixture.activationFacts(pricing, PricingFixture.JULY_1));

        integration.handle(fact);

        JsonAssert.assertThat(PublishedEvents.only(PublishedEvents.of(stream)).payload())
                .isExactlyLike(PricesChangedFixture.priceWithoutDiscountJson());
    }

    @Test
    void publishesTheOpenPriceOfACatchUpThatAlsoClosedAnOlderEntry() {
        PricingModelEditor pricing = PricesChangedFixture.modelWithAnEndedAndAnOpenPriceDue();
        models.save(pricing);
        PricingEvent.PriceActivated ended = PricesChangedFixture.priceActivations(
                PricesChangedFixture.activationFacts(pricing, PricingFixture.JULY_1)).getFirst();

        integration.handle(ended);

        JsonAssert.assertThat(PublishedEvents.only(PublishedEvents.of(stream)).payload())
                .isExactlyLike(PricesChangedFixture.openPriceAfterCatchUpJson());
    }

    @Test
    void publishesNothingForAPriceThatStartedAndEndedBeforeTheFirstPoll() {
        PricingModelEditor pricing = PricesChangedFixture.modelWithOnlyAnEndedPriceDue();
        models.save(pricing);
        PricingEvent.PriceActivated ended = PricesChangedFixture.priceActivated(
                PricesChangedFixture.activationFacts(pricing, PricingFixture.JULY_1));

        integration.handle(ended);

        assertThat(ended.effectiveFrom()).isEqualTo(PricingFixture.MAY_1);
        assertThat(PublishedEvents.of(stream)).isEmpty();
    }

    @Test
    void publishesNothingForADiscountThatEndedBeforeTheFirstPoll() {
        PricingModelEditor pricing = PricesChangedFixture.modelWithOnlyAnEndedDiscountDue();
        models.save(pricing);
        PricingEvent.DiscountActivated ended = PricesChangedFixture.discountActivated(
                PricesChangedFixture.activationFacts(pricing, PricingFixture.JULY_1));

        integration.handle(ended);

        assertThat(PublishedEvents.of(stream)).isEmpty();
    }

    @Test
    void publishesForThePricedCurrencyOnlyWhenADiscountBecomesActive() {
        PricingModelEditor pricing = PricesChangedFixture.modelPricedInPlnAndEurWithAFixedPlnDiscount();
        models.save(pricing);
        PricingEvent.DiscountActivated fact = PricesChangedFixture.discountActivated(
                PricesChangedFixture.activationFacts(pricing, PricingFixture.JULY_1));

        integration.handle(fact);

        JsonAssert.assertThat(PublishedEvents.only(PublishedEvents.of(stream)).payload())
                .isExactlyLike(PricesChangedFixture.fixedPlnDiscountJson());
    }

    @Test
    void publishesNothingWhenAPriceIsScheduled() {
        PricingModelEditor pricing = PricesChangedFixture.modelWithAScheduledPrice();

        assertThat(pricing.events).isNotEmpty().allMatch(PricingEvent.PriceScheduled.class::isInstance);
        assertThat(PublishedEvents.factsHandledBy(PricesIntegration.class)).doesNotContain(PricingEvent.PriceScheduled.class);
        assertThat(PublishedEvents.of(stream)).isEmpty();
    }

    @Test
    void publishesNothingWhenADiscountIsScheduled() {
        PricingModelEditor pricing = PricesChangedFixture.modelWithAScheduledDiscount();

        assertThat(NormalizingPricingRepository.eventsEmittedFrom(pricing))
                .isNotEmpty()
                .hasOnlyElementsOfTypes(PricingEvent.PriceScheduled.class, PricingEvent.DiscountScheduled.class);
        assertThat(PublishedEvents.factsHandledBy(PricesIntegration.class))
                .doesNotContain(PricingEvent.DiscountScheduled.class);
        assertThat(PublishedEvents.of(stream)).isEmpty();
    }

    @Test
    void publishesOnlyTheActivationFacts() {
        assertThat(PublishedEvents.factsHandledBy(PricesIntegration.class))
                .containsExactlyInAnyOrder(PricingEvent.PriceActivated.class, PricingEvent.DiscountActivated.class);
    }
}
