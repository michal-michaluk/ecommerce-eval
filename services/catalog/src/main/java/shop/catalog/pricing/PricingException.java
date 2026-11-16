package shop.catalog.pricing;

import shop.catalog.DomainRefusal;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * The only exception the pricing context raises; the error type owns the message and carries the offending values.
 *
 * <p>It is a {@link DomainRefusal} because a refused pricing command is what 12 answers with `409 {reason}`: the transport reads the
 * constant's name, never the message.
 */
class PricingException extends RuntimeException implements DomainRefusal {

    enum Error {
        CURRENCY_UNKNOWN("currency '%s' is not an ISO 4217 code"),
        AMOUNT_MINOR_NEGATIVE("amountMinor must not be negative, got %s"),
        VALIDITY_RANGE_EMPTY("validUntil %s must be after validFrom %s"),
        BASIS_POINTS_OUT_OF_RANGE("basisPoints must be within 1..10000, got %s"),
        PRICE_ID_MISSING("a price entry id is required"),
        DISCOUNT_ID_MISSING("a discount id is required"),
        PRICE_ID_DUPLICATED("price entry '%s' is already part of this pricing model"),
        PRICE_NOT_FOUND("price entry '%s' is not part of this pricing model"),
        PRICE_VALID_FROM_IN_PAST("price entry '%s' cannot start at %s, which is before now %s"),
        PRICE_RANGE_OVERLAPS(
                "price entry '%s' overlaps price entry '%s' — one open entry per currency is allowed (RULE-3)"),
        PRICE_ALREADY_STARTED(
                "price entry '%s' started at or before %s and can only be closed by a new entry (RULE-8)"),
        DISCOUNT_ID_DUPLICATED("discount '%s' is already part of this pricing model"),
        DISCOUNT_NOT_FOUND("discount '%s' is not part of this pricing model"),
        DISCOUNT_VALID_FROM_IN_PAST("discount '%s' cannot start at %s, which is before now %s"),
        DISCOUNT_ALREADY_STARTED("discount '%s' started at or before %s and cannot be removed (RULE-8)"),
        DISCOUNT_CURRENCY_NOT_PRICED("discount '%s' is bound to a currency this offer has no price entry for (RULE-5)"),
        ACTIVE_PRICE_INCOMPLETE("an active price carries an amount and an absent price carries none"),
        DISCOUNT_POLICY_INCOMPLETE("a discount policy requires an order and a stacking"),
        OFFER_UNKNOWN("offer %s does not exist"),
        PRICING_MODEL_UNKNOWN("offer %s has no pricing model to publish");

        private final String template;

        Error(String template) {
            this.template = template;
        }

        String format(Object... details) {
            return template.formatted(details);
        }
    }

    private final Error error;
    private final List<Object> details;

    PricingException(Error error, Object... details) {
        super(error.format(details));
        this.error = error;
        this.details = Collections.unmodifiableList(Arrays.asList(details));
    }

    PricingException(Error error, Throwable cause, Object... details) {
        super(error.format(details), cause);
        this.error = error;
        this.details = Collections.unmodifiableList(Arrays.asList(details));
    }

    Error error() {
        return error;
    }

    List<Object> details() {
        return details;
    }

    @Override
    public String reason() {
        return error.name();
    }
}
