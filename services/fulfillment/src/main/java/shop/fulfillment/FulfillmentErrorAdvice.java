package shop.fulfillment;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

import shop.fulfillment.kernel.FulfillmentException;

/**
 * The one advice that turns this context's refusals into the wire (element 11's status table): an incomplete order is
 * {@code 422} and names the components it is missing, an unknown resource is {@code 404}, and a refused command is
 * {@code 409} with the code the contract gives that refusal.
 *
 * <p>It reads the error type and the domain values the exception carries, never its message — the message is a log line
 * ({@code http.rejection-status}). Anything the table does not name is rethrown so the framework answers {@code 500}
 * without a message or a stack trace, which is also what a warehouse failure does: it is surfaced, never smoothed
 * (element 13 RULE-7).
 *
 * <p>The {@code 401} of a missing token and the {@code 403} of a missing scope are the framework's and stay empty; the empty
 * bodies come from the entry point and the access-denied handler in {@link AppConfiguration}.
 */
@RestControllerAdvice
class FulfillmentErrorAdvice {

    /** The payload path a duplicated product line points the client at, in the same shape as a missing part. */
    private static final String DUPLICATED_LINE = "lines.productNumber";

    /** Element 11's code for a position a path segment addresses that this order does not have. */
    private static final String POSITION_NOT_FOUND = "POSITION_NOT_FOUND";

    /** Element 11's code for both commands a hold blocks — the code is not the aggregate's error name. */
    private static final String ON_HOLD = "ON_HOLD";

    /** Element 11's derived code for a command that needs an employee to hold the order first. */
    private static final String NOT_STARTED = "NOT_STARTED";

    /**
     * One status per refusal this context raises.
     *
     * @param failure the refusal
     * @return the answer, or the same failure rethrown when the table does not own it
     */
    @ExceptionHandler(FulfillmentException.class)
    ResponseEntity<Object> refused(FulfillmentException failure) {
        return switch (failure.error()) {
            case ORDER_INCOMPLETE, ORDER_LINE_DUPLICATE -> incomplete(failure);
            case PROCESSING_NOT_FOUND, POSITION_UNKNOWN -> notFound(failure);
            case ASSIGNMENT_REFUSED -> conflict(decision(failure));
            case POSITION_NOT_PICKED, ACTOR_NOT_ALLOWED -> conflict(failure.error().name());
            case WORK_IS_ON_HOLD -> conflict(ON_HOLD);
            case WORK_NOT_STARTED -> conflict(NOT_STARTED);
            default -> throw failure;
        };
    }

    private static ResponseEntity<Object> incomplete(FulfillmentException failure) {
        if (failure.error() == FulfillmentException.Error.ORDER_LINE_DUPLICATE) {
            return ResponseEntity.status(422)
                    .body(new Incomplete(FulfillmentException.Error.ORDER_INCOMPLETE.name(), List.of(DUPLICATED_LINE)));
        }
        return ResponseEntity.status(422).body(new Incomplete(failure.error().name(), missing(failure)));
    }

    private static ResponseEntity<Object> notFound(FulfillmentException failure) {
        String code = failure.error() == FulfillmentException.Error.POSITION_UNKNOWN
                ? POSITION_NOT_FOUND
                : failure.error().name();
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new Reason(code, null));
    }

    private static ResponseEntity<Object> conflict(String code) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new Reason(code, null));
    }

    /** The components the order is missing, as {@code Order} named them when it refused the handover. */
    @SuppressWarnings("unchecked")
    private static List<String> missing(FulfillmentException failure) {
        return failure.details().stream()
                .filter(List.class::isInstance)
                .map(detail -> (List<String>) detail)
                .findFirst()
                .orElse(List.of());
    }

    /** The guard's own reason for refusing a claim, or the error name when a refusal carries none. */
    private static String decision(FulfillmentException failure) {
        return failure.details().stream()
                .filter(Enum.class::isInstance)
                .map(detail -> ((Enum<?>) detail).name())
                .findFirst()
                .orElseGet(() -> failure.error().name());
    }

    /** {@code 422}: which of the order's components the handover did not carry. */
    record Incomplete(String error, List<String> missing) {
    }

    /** {@code 404}/{@code 409}: the one value a client branches on, plus the blocking values when the refusal names any. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record Reason(String error, List<String> reasons) {
    }
}
