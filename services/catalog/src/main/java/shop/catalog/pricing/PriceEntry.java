package shop.catalog.pricing;

import java.time.Instant;
import java.util.Objects;

/**
 * One price of one offer in one currency over one half-open range (03 RULE-2). A change never overwrites an entry: the
 * predecessor is closed and a new entry is appended, so the series of entries is the history. {@code activated} is the
 * RULE-9 marker — it says an activation event has already left the context for this entry.
 */
record PriceEntry(PriceId priceId, Money price, Validity validity, boolean activated) {

    PriceEntry {
        Objects.requireNonNull(priceId, "priceId");
        Objects.requireNonNull(price, "price");
        Objects.requireNonNull(validity, "validity");
    }

    static PriceEntry scheduled(PriceId priceId, Money price, Validity validity) {
        return new PriceEntry(priceId, price, validity, false);
    }

    boolean isActiveAt(Instant at) {
        return validity.isActiveAt(at);
    }

    boolean isActiveIn(String currency, Instant at) {
        return price.isIn(currency) && validity.isActiveAt(at);
    }

    boolean hasStartedAt(Instant at) {
        return validity.hasStartedAt(at);
    }

    boolean isScheduledAt(Instant at) {
        return validity.isFutureAt(at);
    }

    boolean isHistoricAt(Instant at) {
        return validity.hasEndedBy(at);
    }

    boolean isDueAt(Instant at) {
        return !activated && validity.hasStartedAt(at);
    }

    PriceEntry markedActivated() {
        return new PriceEntry(priceId, price, validity, true);
    }

    PriceEntry closedAt(Instant end) {
        return new PriceEntry(priceId, price, validity.closedAt(end), activated);
    }

    PriceEntry reopenedUntil(Instant end) {
        return new PriceEntry(priceId, price, new Validity(validity.validFrom(), end), activated);
    }
}
