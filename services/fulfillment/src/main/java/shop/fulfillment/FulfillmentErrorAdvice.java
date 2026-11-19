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
 * {@code 422} and names the components it is missing, an unknown processing is {@code 404}, and a refused command is
 * {@code 409} with the reason the decision table used.
 *
 * <p>It reads the error type and the domain values the exception carries, never its message — the message is a log line
 * ({@code http.rejection-status}). Anything the table does not name is rethrown so the framework answers {@code 500}
 * without a message or a stack trace.
 *
 * <p>The {@code 401} of a missing token and the {@code 403} of a missing scope are the framework's and stay empty; the empty
 * bodies come from the entry point and the access-denied handler in {@link AppConfiguration}.
 */
@RestControllerAdvice
class FulfillmentErrorAdvice {

    /** The payload path a duplicated product line points the client at, in the same shape as a missing part. */
    private static final String DUPLICATED_LINE = "lines.productNumber";

    /**
     * One status per refusal this context raises.
     *
     * @param failure the refusal
     * @return the answer, or the same failure rethrown when the table does not own it
     */
    @ExceptionHandler(FulfillmentException.class)
    ResponseEntity<Object> refused(FulfillmentException failure) {
        return switch (failure.error()) {
            case ORDER_INCOMPLETE -> ResponseEntity.status(422)
                    .body(new Incomplete(failure.error().name(), missing(failure)));
            case ORDER_LINE_DUPLICATE -> ResponseEntity.status(422)
                    .body(new Incomplete(FulfillmentException.Error.ORDER_INCOMPLETE.name(), List.of(DUPLICATED_LINE)));
            case PROCESSING_NOT_FOUND -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new Reason(failure.error().name(), null));
            case ASSIGNMENT_REFUSED -> ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(new Reason(decision(failure), null));
            default -> throw failure;
        };
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
