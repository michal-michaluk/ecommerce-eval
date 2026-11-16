package shop.catalog.reads;

import shop.catalog.pricing.PricingChanged;

import java.time.Instant;
import java.util.Objects;

/**
 * One price of one offer in one currency over one half-open range, as the pricing view of 16 lists it: the identity a sales person
 * edits by, the amount in minor units with its currency (D1) and the range it is valid over.
 *
 * <p>Which list it belongs to — current, scheduled or history — is not stored: it is a question about the clock, so
 * {@link PricingReads} answers it when the screen is rendered ({@code projection.derived-field-stored} waived for the same reason
 * {@link OfferVisibility} is not stored).
 */
record PricedItem(String priceId, int amountMinor, String currency, Instant validFrom, Instant validUntil) {

    PricedItem {
        priceId = Objects.requireNonNull(priceId, "priceId");
        currency = Objects.requireNonNull(currency, "currency");
        validFrom = Objects.requireNonNull(validFrom, "validFrom");
    }

    static PricedItem of(PricingChanged.PriceRow row) {
        return new PricedItem(row.priceId(), row.amountMinor(), row.currency(), row.validFrom(), row.validUntil());
    }

    /** 03 RULE-2's half-open range: active at exactly {@code validFrom}, no longer active at exactly {@code validUntil}. */
    boolean isActiveAt(Instant at) {
        return !at.isBefore(validFrom) && (validUntil == null || at.isBefore(validUntil));
    }

    boolean isScheduledAt(Instant at) {
        return validFrom.isAfter(at);
    }

    boolean isHistoricAt(Instant at) {
        return validUntil != null && !validUntil.isAfter(at);
    }
}
