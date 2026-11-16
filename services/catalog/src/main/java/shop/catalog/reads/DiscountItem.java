package shop.catalog.reads;

import shop.catalog.pricing.PricingChanged;

import java.time.Instant;
import java.util.Objects;

/**
 * One promotion on the price of one offer, as the pricing view of 16 lists it: a percentage in basis points or a fixed amount in its
 * own currency, over the half-open range it applies to (03 RULE-5).
 *
 * <p>The two families stay distinguishable by {@link Kind} and each keeps the number it uses, so nothing has to be inferred from a
 * null: a percentage carries {@code basisPoints}, a fixed amount an amount and a currency. A percentage applies to every currency, a
 * fixed amount only to the one it is expressed in.
 */
record DiscountItem(String discountId, Kind kind, int basisPoints, int amountMinor, String currency,
                    Instant validFrom, Instant validUntil) {

    DiscountItem {
        discountId = Objects.requireNonNull(discountId, "discountId");
        kind = Objects.requireNonNull(kind, "kind");
        validFrom = Objects.requireNonNull(validFrom, "validFrom");
    }

    static DiscountItem of(PricingChanged.DiscountRow row) {
        return new DiscountItem(row.discountId(), Kind.valueOf(row.kind().name()), row.basisPoints(), row.amountMinor(),
                row.currency(), row.validFrom(), row.validUntil());
    }

    boolean appliesTo(String otherCurrency) {
        return kind == Kind.PERCENTAGE || otherCurrency.equals(currency);
    }

    boolean isActiveAt(Instant at) {
        return !at.isBefore(validFrom) && (validUntil == null || at.isBefore(validUntil));
    }

    boolean isScheduledAt(Instant at) {
        return validFrom.isAfter(at);
    }

    boolean isExpiredAt(Instant at) {
        return validUntil != null && !validUntil.isAfter(at);
    }

    /** The two families of 03 RULE-5 as this context states them. */
    enum Kind {
        PERCENTAGE,
        FIXED
    }
}
