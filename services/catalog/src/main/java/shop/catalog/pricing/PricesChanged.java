package shop.catalog.pricing;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Element 13 — the offer's price became what a customer pays: one active price with its scale, the discounts active next to it and the
 * instant the change took effect (RULE-3). It fires when a price or a discount <em>becomes active</em> (03 RULE-4), never when one is
 * scheduled: a scheduled entry is silent outside the context.
 *
 * <p>{@code effectiveFrom} is the entry's own {@code validFrom}, so a poll that catches up after downtime reports the same effective
 * date the shop would have seen had the service stayed up, while the envelope's {@code occurredAt} stays the moment of the poll
 * (RULE-8).
 *
 * <p>{@code currency} names the open price entry the amount belongs to: an event never carries a price for a currency the offer has no
 * open entry for (RULE-7).
 */
public record PricesChanged(
        String productId,
        String currency,
        Price price,
        List<AppliedDiscount> discounts,
        Instant effectiveFrom) {

    public static final String TYPE = "PricesChanged";

    public PricesChanged {
        productId = Objects.requireNonNull(productId, "productId");
        currency = Objects.requireNonNull(currency, "currency");
        price = Objects.requireNonNull(price, "price");
        effectiveFrom = Objects.requireNonNull(effectiveFrom, "effectiveFrom");
        discounts = List.copyOf(discounts);
    }

    static PricesChanged of(String productId, Money price, Instant effectiveFrom, List<Discount> discounts) {
        return new PricesChanged(
                productId,
                price.currency(),
                new Price(price.amountMinor(), price.scale()),
                discounts.stream().map(PricesChanged::discountOf).toList(),
                effectiveFrom);
    }

    private static AppliedDiscount discountOf(Discount discount) {
        return switch (discount.kind()) {
            case Discount.Kind.Percentage percentage -> new AppliedDiscount(
                    "percentage", percentage.basisPoints(), null, null, discount.validity().validUntil());
            case Discount.Kind.Fixed fixed -> new AppliedDiscount(
                    "fixed", null, fixed.amount().amountMinor(), fixed.amount().currency(),
                    discount.validity().validUntil());
        };
    }

    /** Money as 03 RULE-1 stores it: minor units plus the scale of the currency they are expressed in (D1). */
    public record Price(int amountMinor, int scale) {
    }

    /** One discount the shop applies on top of the price; a percentage carries basis points, a fixed one an amount in its currency. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record AppliedDiscount(String kind, Integer basisPoints, Integer amountMinor, String currency, Instant validUntil) {
    }
}
