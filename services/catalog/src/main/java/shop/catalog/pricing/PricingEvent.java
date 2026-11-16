package shop.catalog.pricing;

import java.time.Instant;

/**
 * The facts the pricing model records, one nested record per fact. Scheduling is silent (RULE-4): a scheduled entry
 * emits the in-context {@code *Scheduled} fact at the moment a human decided it, nothing leaves the context, and only
 * an entry that became due emits {@code *Activated} — the fact 13 RULE-3 publishes as {@code PricesChanged}, carrying
 * {@code occurredAt} (the poll) and {@code effectiveFrom} (the entry's {@code validFrom}, 03 RULE-9).
 */
interface PricingEvent {

    record PriceScheduled(String offerId, PriceId priceId, Money price, Validity validity) implements PricingEvent {
    }

    record PriceRemoved(String offerId, PriceId priceId) implements PricingEvent {
    }

    record PriceActivated(String offerId, Money price, Instant effectiveFrom, Instant occurredAt)
            implements PricingEvent {
    }

    record DiscountScheduled(String offerId, DiscountId discountId, Discount.Kind kind, Validity validity)
            implements PricingEvent {
    }

    record DiscountRemoved(String offerId, DiscountId discountId) implements PricingEvent {
    }

    record DiscountActivated(String offerId, DiscountId discountId, Instant effectiveFrom, Instant occurredAt)
            implements PricingEvent {
    }
}
