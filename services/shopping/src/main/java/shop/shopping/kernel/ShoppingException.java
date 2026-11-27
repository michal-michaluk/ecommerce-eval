package shop.shopping.kernel;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * The only exception this service raises. An unchecked failure carries the error type — which owns the message — and the
 * domain values a caller or an adapter may need, so no caller matches on a message string and no domain error needs its own
 * class (register D48, {@code error-handling.md}).
 *
 * <p>It lives in {@code kernel/} because it is the one package both slices of the context may read: the money type, the
 * basket aggregate and the persistence adapter all refuse with this type, and no slice may name another slice's type.
 *
 * <p>It is transport-free: no status code, no header, no HTTP type. The service's error advice maps {@link #error()} to a
 * status and a code and renders {@link #details()} as the offending values.
 */
public class ShoppingException extends RuntimeException {

    /**
     * The service's whole error vocabulary, each constant owning its message template.
     *
     * <p>The specification names these by code: {@code QUANTITY_NOT_POSITIVE}, {@code CURRENCY_MISMATCH},
     * {@code BASKET_NOT_FOUND}, {@code OFFER_NOT_FOUND}, {@code NOTHING_SELECTED}, {@code BLOCKED_BY_ITEMS},
     * {@code BASKET_INACTIVE} and {@code OFFER_READ_UNAVAILABLE} (element 07 §Error contract, element 09 §Failure outcomes).
     * {@code CONCURRENT_MODIFICATION} is the doctrine's own name for a write that lost the race
     * ({@code error-handling.md}, register D48), not an element-07 code.
     *
     * <p>The remaining constants are the refusals a rule states but leaves unnamed: the quantity of an {@code unavailable}
     * row cannot change (D19), an amount is never stored negative and a discount is never added to a charge (element 03
     * RULE-6), the fine unit cannot hold the result (element 03 RULE-3/RULE-4), a code without a decimal scale carries no
     * amount (element 03 RULE-2, D21) and a merged quantity cannot leave the range a basket holds (element 02 RULE-4).
     *
     * <p>{@code HANDOVER_STALE} is the checkout slice's own refusal, and no element-07 code names it: element 09 §Failure
     * outcomes answers "checkout slice rejects the payload" with a {@code 500}, which is what an unmapped code already is, so
     * the advice rethrows it instead of borrowing {@code CONCURRENT_MODIFICATION} — that code is the basket's own lost race,
     * and the client retries it (D62).
     */
    public enum Error {
        QUANTITY_NOT_POSITIVE("quantity must be at least 1, got %s"),
        QUANTITY_OUT_OF_RANGE("quantity %s cannot grow by %s without leaving the range a basket holds"),
        CURRENCY_MISMATCH("cannot use %s where %s is expected, no conversion between currencies exists"),
        AMOUNT_NEGATIVE("amount %s is negative, a discount is a positive amount of kind discount"),
        AMOUNT_WOULD_BE_NEGATIVE("subtracting %s from %s %s would be negative, an amount never is"),
        AMOUNT_KIND_MISMATCH("cannot combine a %s amount with a %s amount, a discount is subtracted not added"),
        AMOUNT_OUT_OF_RANGE("the amount %s cannot be expressed in the fine unit of %s"),
        CURRENCY_UNSUPPORTED("%s is not an ISO-4217 currency with a decimal scale, it cannot carry an amount"),
        BASKET_NOT_FOUND("no basket %s"),
        OFFER_NOT_FOUND("offer %s is not in the basket"),
        OFFER_UNAVAILABLE("offer %s is unavailable, its quantity cannot change"),
        NOTHING_SELECTED("nothing is selected in basket %s, there is nothing to hand over"),
        BLOCKED_BY_ITEMS("basket %s cannot be handed over, blocked by %s"),
        BASKET_INACTIVE("basket %s is %s, only an active basket changes"),
        CONCURRENT_MODIFICATION("basket %s was changed by someone else"),
        HANDOVER_STALE("basket %s handed over revision %s, checkout already holds %s"),
        OFFER_READ_UNAVAILABLE("offer %s could not be read");

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

    public ShoppingException(Error error, Object... details) {
        super(error.format(details));
        this.error = error;
        this.details = Collections.unmodifiableList(Arrays.asList(details));
    }

    public ShoppingException(Error error, Throwable cause, Object... details) {
        super(error.format(details), cause);
        this.error = error;
        this.details = Collections.unmodifiableList(Arrays.asList(details));
    }

    /**
     * The error type this failure carries — the name of the constant is the reason a client branches on, so the transport
     * reads it instead of matching a message.
     *
     * @return the error type
     */
    public Error error() {
        return error;
    }

    /**
     * The domain values the error template was formatted with, so an adapter can name them on the wire without parsing the
     * message. The list is a copy: no caller may change what this failure carries.
     *
     * @return the detail values, in the order the template names them
     */
    public List<Object> details() {
        return Collections.unmodifiableList(new ArrayList<>(details));
    }
}
