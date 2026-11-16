package shop.catalog.pricing;

import java.time.Instant;
import java.util.Objects;

/**
 * RULE-2 — a half-open validity range {@code [validFrom, validUntil)}; an absent {@code validUntil} is open-ended, so
 * an entry is active at exactly {@code validFrom} and no longer active at exactly {@code validUntil}.
 */
record Validity(Instant validFrom, Instant validUntil) {

    Validity {
        Objects.requireNonNull(validFrom, "validFrom");
        if (validUntil != null && !validUntil.isAfter(validFrom)) {
            throw new PricingException(PricingException.Error.VALIDITY_RANGE_EMPTY, validUntil, validFrom);
        }
    }

    static Validity startingAt(Instant validFrom) {
        return new Validity(validFrom, null);
    }

    static Validity between(Instant validFrom, Instant validUntil) {
        return new Validity(validFrom, validUntil);
    }

    boolean isOpenEnded() {
        return validUntil == null;
    }

    boolean isActiveAt(Instant at) {
        return hasStartedAt(at) && (validUntil == null || at.isBefore(validUntil));
    }

    boolean hasStartedAt(Instant at) {
        return !at.isBefore(validFrom);
    }

    boolean isFutureAt(Instant at) {
        return validFrom.isAfter(at);
    }

    boolean hasEndedBy(Instant at) {
        return validUntil != null && !validUntil.isAfter(at);
    }

    Validity closedAt(Instant end) {
        return new Validity(validFrom, end);
    }
}
