package shop.catalog.pricing;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * Element 17's {@code pricing → reads} wire: the whole price series of one offer and every discount it carries, in the pricing
 * context's own published vocabulary, so the read side can copy them without naming an internal value.
 *
 * <p>It is a second record beside the {@link PricingModel} snapshot because that snapshot cannot leave the context: its components
 * ({@code PriceEntry}, {@code Discount}, {@code Money}, {@code Validity}) are package-private, and it names no {@code offerId} — a
 * listener could neither key a row by it nor read one entry out of it. This payload states the same fact flattened: one row per
 * entry, one row per discount, both as the read side stores them ({@code projection.copy-of-foreign-payload}).
 *
 * <p>{@code prices} is the whole series, never a partition: which entry is current, scheduled or history is a question about the
 * clock, and only {@code reads} answers clock questions (17 RULE-6). The order is {@code validFrom} ascending so two payloads of the
 * same model are equal.
 */
public record PricingChanged(String offerId, List<PriceRow> prices, List<DiscountRow> discounts) {

    public PricingChanged {
        offerId = Objects.requireNonNull(offerId, "offerId");
        prices = List.copyOf(prices);
        discounts = List.copyOf(discounts);
    }

    static PricingChanged of(String offerId, PricingModel model) {
        return new PricingChanged(offerId,
                allPricesOf(model).map(PriceRow::of).toList(),
                model.discounts().stream().map(DiscountRow::of).toList());
    }

    private static Stream<PriceEntry> allPricesOf(PricingModel model) {
        return Stream.of(model.current(), model.scheduled(), model.history()).flatMap(List::stream);
    }

    /** One price entry: the amount in minor units with its currency, over the half-open range 03 RULE-2 stores. */
    public record PriceRow(String priceId, int amountMinor, String currency, Instant validFrom, Instant validUntil) {

        static PriceRow of(PriceEntry entry) {
            return new PriceRow(entry.priceId().value(), entry.price().amountMinor(), entry.price().currency(),
                    entry.validity().validFrom(), entry.validity().validUntil());
        }
    }

    /**
     * One discount, flat: a percentage carries {@code basisPoints}, a fixed one an amount in its own currency. The two families
     * stay distinguishable by {@link Kind} instead of by two record types, so the payload needs no type discriminator to be stored
     * as JSON.
     */
    public record DiscountRow(String discountId, Kind kind, int basisPoints, int amountMinor, String currency,
                              Instant validFrom, Instant validUntil) {

        static DiscountRow of(Discount discount) {
            return switch (discount.kind()) {
                case Discount.Kind.Percentage percentage -> new DiscountRow(discount.discountId().value(),
                        Kind.PERCENTAGE, percentage.basisPoints(), 0, null,
                        discount.validity().validFrom(), discount.validity().validUntil());
                case Discount.Kind.Fixed fixed -> new DiscountRow(discount.discountId().value(), Kind.FIXED, 0,
                        fixed.amount().amountMinor(), fixed.amount().currency(),
                        discount.validity().validFrom(), discount.validity().validUntil());
            };
        }

        /** The two families of 03 RULE-5; a fixed amount only counts against a price in its own currency. */
        public enum Kind {
            PERCENTAGE,
            FIXED
        }
    }
}
