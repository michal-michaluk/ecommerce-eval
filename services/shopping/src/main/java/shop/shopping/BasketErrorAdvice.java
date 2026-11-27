package shop.shopping;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

import shop.shopping.kernel.HandoverRefusal;
import shop.shopping.kernel.ShoppingException;

/**
 * The one advice that turns the basket's refusals into the wire (element 07 §Error contract, register D48): the code element 07
 * names for the failure, the status element 07 gives it, and the domain values a client can branch on — never the message,
 * which is a log line ({@code http.rejection-status}).
 *
 * <p>The two hundred-level failures are the framework's: a missing token is {@code 401} and a missing scope {@code 403}, both
 * with empty bodies, and a malformed body is {@code 400}. The advice owns exactly the five statuses the contract states:
 * {@code 404} for an identity nobody knows, {@code 422} for a command the basket refuses (the recorded deviation D44 — the
 * contract of record writes it as {@code 422}, not as the doctrine's {@code 409}), {@code 409} for a write that lost the race
 * (D62 — retryable), and {@code 503} for an offer that could not be read and has no snapshot to grey a row with.
 *
 * <p>{@code BLOCKED_BY_ITEMS} carries {@code reasons[]} — the rows that refuse the handover, each {@code {offerId, status,
 * stale}} — and it is the only code that does. The array is always an array of objects, never a list of strings, because the
 * client greys exactly the rows it names (D33, element 01 §States).
 *
 * <p>Any other error the vocabulary holds is rethrown, so the framework answers {@code 500} without a message: an amount that
 * leaves the fine unit or a currency ISO-4217 does not know is nobody's refusal.
 */
@RestControllerAdvice
class BasketErrorAdvice {

    /**
     * One status per refusal this service raises.
     *
     * @param failure the refusal
     * @return the answer, or the same failure rethrown when the contract gives it no status
     */
    @ExceptionHandler(ShoppingException.class)
    ResponseEntity<Object> refused(ShoppingException failure) {
        return switch (failure.error()) {
            case BASKET_NOT_FOUND, OFFER_NOT_FOUND -> notFound(failure);
            case QUANTITY_NOT_POSITIVE, CURRENCY_MISMATCH, NOTHING_SELECTED, BLOCKED_BY_ITEMS, BASKET_INACTIVE,
                    OFFER_UNAVAILABLE -> unprocessable(failure);
            case CONCURRENT_MODIFICATION -> code(HttpStatus.CONFLICT, failure);
            case OFFER_READ_UNAVAILABLE -> code(HttpStatus.SERVICE_UNAVAILABLE, failure);
            default -> throw failure;
        };
    }

    /** {@code 404}: the identity the path or the body named, so a client can tell which of its two arguments was wrong. */
    private static ResponseEntity<Object> notFound(ShoppingException failure) {
        String identity = namedIdentity(failure);
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                failure.error() == ShoppingException.Error.OFFER_NOT_FOUND
                        ? new Refusal(failure.error().name(), null, identity, null)
                        : new Refusal(failure.error().name(), identity, null, null));
    }

    private static ResponseEntity<Object> unprocessable(ShoppingException failure) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_CONTENT).body(failure.error()
                == ShoppingException.Error.BLOCKED_BY_ITEMS
                        ? new Refusal(failure.error().name(), null, null, blockedRowsOf(failure))
                        : new Refusal(failure.error().name(), null, null, null));
    }

    private static ResponseEntity<Object> code(HttpStatus status, ShoppingException failure) {
        return ResponseEntity.status(status).body(new Refusal(failure.error().name(), null, null, null));
    }

    /** The offending identity, which every {@code 404} carries as its first string detail — the path segment the caller sent. */
    private static String namedIdentity(ShoppingException failure) {
        return failure.details().stream()
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .findFirst()
                .orElse(null);
    }

    /** The rows a handover was blocked by, as the aggregate spelled them — the wire shape of {@code reasons[]}. */
    @SuppressWarnings("unchecked")
    private static List<HandoverRefusal> blockedRowsOf(ShoppingException failure) {
        return failure.details().stream()
                .filter(List.class::isInstance)
                .map(detail -> (List<HandoverRefusal>) detail)
                .findFirst()
                .orElse(List.of());
    }

    /**
     * The one value a client branches on, the identity a {@code 404} names, and the rows that blocked a handover.
     *
     * @param error   element 07's code
     * @param basketId the basket a {@code 404} did not find
     * @param offerId  the offer a {@code 404} did not find
     * @param reasons  the rows a {@code BLOCKED_BY_ITEMS} names, {@code null} for every other code
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record Refusal(String error, String basketId, String offerId, List<HandoverRefusal> reasons) {
    }
}
