package shop.catalog.pricing;

import java.time.Instant;

/** The facts a save is expected to publish, one factory per event, so a test says what it expects in business terms. */
public class PricingEventFixture {

    public static PricingEvent.PriceScheduled priceScheduled(
            String priceId, Money price, Instant validFrom, Instant validUntil) {
        return new PricingEvent.PriceScheduled(PricingFixture.OFFER_ID, PricingFixture.priceId(priceId), price,
                PricingFixture.until(validFrom, validUntil));
    }

    public static PricingEvent.DiscountScheduled discountScheduled(
            String discountId, Discount.Kind kind, Instant validFrom, Instant validUntil) {
        return new PricingEvent.DiscountScheduled(PricingFixture.OFFER_ID, PricingFixture.discountId(discountId), kind,
                PricingFixture.until(validFrom, validUntil));
    }

    public static PricingEvent.PriceActivated priceActivated(
            String offerId, Money price, Instant effectiveFrom, Instant occurredAt) {
        return new PricingEvent.PriceActivated(offerId, price, effectiveFrom, occurredAt);
    }

    public static PricingEvent.DiscountActivated discountActivated(
            String offerId, String discountId, Instant effectiveFrom, Instant occurredAt) {
        return new PricingEvent.DiscountActivated(offerId, PricingFixture.discountId(discountId), effectiveFrom,
                occurredAt);
    }
}
