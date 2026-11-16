package shop.catalog.pricing;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * 12's single pricing edit (D18) in this context's own terms: the entries and discounts to append and the identities to remove — all
 * four components applied to one model in one transaction, so a refused removal takes the entries of the same request with it.
 *
 * <p>{@code validFrom} absent means "starts now", resolved against the instant the service opens the command with rather than a clock
 * the transport read a moment earlier: 03 RULE-7/8 refuse a range that starts before the command's own now. Identities are generated
 * here for the same reason — a new entry has no identity until this context gives it one (03 RULE-2).
 *
 * <p>The four components mirror 12's body because that body <em>is</em> this command: D18's fields are the four things one pricing
 * edit can say.
 */
record PricingEdits(
        @Valid List<Amount> prices,
        @Valid List<Promotion> discounts,
        List<String> removePriceIds,
        List<String> removeDiscountIds) {

    private static final int ID_LENGTH = 8;

    PricingEdits {
        prices = copied(prices);
        discounts = copied(discounts);
        removePriceIds = copied(removePriceIds);
        removeDiscountIds = copied(removeDiscountIds);
    }

    /**
     * The removals run before the additions, so one request can express a replacement: dropping a scheduled entry first frees the range
     * the new entry of the same currency would otherwise overlap (03 RULE-3, {@code PRICE_RANGE_OVERLAPS}).
     */
    void applyTo(PricingModelEditor pricing, Instant now) {
        removePriceIds.forEach(priceId -> pricing.removePrice(new PriceId(priceId), now));
        removeDiscountIds.forEach(discountId -> pricing.removeDiscount(new DiscountId(discountId), now));
        prices.forEach(amount -> pricing.schedulePrice(amount.change(now), now));
        discounts.forEach(promotion -> pricing.scheduleDiscount(promotion.change(now), now));
    }

    /**
     * One price 12 asked for: an open entry from {@code validFrom}, or from now, in the currency it states (03 RULE-2).
     *
     * <p>Both components are required, so a body that omits the amount is 12's `400` instead of a free product: the amount is a boxed
     * {@code Integer} for exactly that reason, because the primitive would silently mean {@code 0} and {@code 0} is a real price (10).
     */
    record Amount(@NotBlank String currency, @NotNull @PositiveOrZero Integer amountMinor, Instant validFrom) {

        PriceChange change(Instant now) {
            return new PriceChange(new PriceId(identifier("P")), new Money(amountMinor, currency),
                    Validity.startingAt(validFrom == null ? now : validFrom));
        }
    }

    /** One discount 12 asked for: a percentage in basis points or a fixed amount in its own currency (03 RULE-5). */
    record Promotion(@NotNull Kind kind, Integer basisPoints, Integer amountMinor, String currency,
                     Instant validFrom, Instant validUntil) {

        /**
         * The numbers the chosen family needs, validated here so 12 answers `400` rather than a refusal of a command it could not read
         * (12's 400 row names a bad {@code basisPoints}): a percentage needs 1..10000, a fixed amount needs an amount and a currency.
         */
        @AssertTrue(message = "a discount needs the numbers of the family it states")
        boolean isComplete() {
            if (kind == null) {
                return true;
            }
            return kind == Kind.FIXED
                    ? amountMinor != null && currency != null && !currency.isBlank()
                    : basisPoints != null && basisPoints > 0 && basisPoints <= Discount.Kind.Percentage.MAX_BASIS_POINTS;
        }

        DiscountChange change(Instant now) {
            return new DiscountChange(new DiscountId(identifier("D")), discountKind(), validity(now));
        }

        private Discount.Kind discountKind() {
            return kind == Kind.FIXED
                    ? new Discount.Kind.Fixed(new Money(amountMinor, currency))
                    : new Discount.Kind.Percentage(basisPoints);
        }

        private Validity validity(Instant now) {
            Instant from = validFrom == null ? now : validFrom;
            return validUntil == null ? Validity.startingAt(from) : Validity.between(from, validUntil);
        }

        /** The two families of 03 RULE-5 as 12 spells them in the body. */
        enum Kind {
            PERCENTAGE,
            FIXED;

            @JsonValue
            String wire() {
                return name().toLowerCase(Locale.ROOT);
            }

            @JsonCreator
            static Kind of(String wire) {
                return valueOf(wire.toUpperCase(Locale.ROOT));
            }
        }
    }

    private static <T> List<T> copied(List<T> values) {
        return values == null ? List.of() : List.copyOf(values);
    }

    private static String identifier(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, ID_LENGTH).toUpperCase(Locale.ROOT);
    }
}
