package shop.shopping;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import shop.shopping.kernel.HandoverRefusal;
import shop.shopping.kernel.ShoppingException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.exc.MismatchedInputException;
import tools.jackson.databind.exc.UnrecognizedPropertyException;

/**
 * The one advice that turns the basket's refusals into the wire (element 07 §Error contract, register D48): the code element 07
 * names for the failure, the status element 07 gives it, and the domain values a client can branch on — never the message,
 * which is a log line ({@code http.rejection-status}).
 *
 * <p>It owns every status the contract states, the {@code 400} included. The two authentication failures stay the framework's —
 * a missing token is {@code 401} and a missing scope {@code 403}, both with empty bodies — and so are {@code 406} and
 * {@code 415}, which describe the envelope rather than the command. A body the command cannot take is the one exception: a
 * violated constraint on the command record ({@code @Valid}) and a body that does not read as the command are both answered
 * {@code 400} with element 07's {@code {"errors": [{"field", "message"}]}}, because the framework's own error page reports the
 * servlet's path and not the component the client has to fix.
 *
 * <p>The four remaining statuses are the advice's too: {@code 404} for an identity nobody knows, {@code 422} for a command the
 * basket refuses (the recorded deviation D44 — the contract of record writes it as {@code 422}, not as the doctrine's
 * {@code 409}), {@code 409} for a write that lost the race (D62 — retryable), and {@code 503} for an offer that could not be
 * read and has no snapshot to grey a row with.
 *
 * <p>{@code BLOCKED_BY_ITEMS} carries {@code reasons[]} — the rows that refuse the handover, each {@code {offerId, status,
 * stale}} — and it is the only code that does. The array is always an array of objects, never a list of strings, because the
 * client greys exactly the rows it names (D33, element 01 §States).
 *
 * <p>Any other error the vocabulary holds is rethrown, so the framework answers {@code 500} without a message: an amount that
 * leaves the fine unit or a currency ISO-4217 does not know is nobody's refusal, and so is a handover the checkout slice turned
 * away (element 09 §Failure outcomes).
 *
 * <p>Two refusals are also an incident, and they are stated once each here, where every answer passes: a write that lost the
 * race — retryable, and the only failure a {@code GET} can answer (D62) — with the basket and the route it lost on, and a
 * handover the customer could not complete, with the basket and the offers that blocked it. Neither line carries a token or a
 * body.
 */
@RestControllerAdvice
class BasketErrorAdvice {

    private static final Logger log = LoggerFactory.getLogger(BasketErrorAdvice.class);

    private static final String NOT_A_COMPONENT = " is not a component of this command";

    private static final String UNREADABLE_COMMAND = "the body does not read as this command";

    /**
     * One status per refusal this service raises.
     *
     * @param failure the refusal
     * @param request the route that was answered, named on the one line a lost race costs an operator
     * @return the answer, or the same failure rethrown when the contract gives it no status
     */
    @ExceptionHandler(ShoppingException.class)
    ResponseEntity<Object> refused(ShoppingException failure, HttpServletRequest request) {
        return switch (failure.error()) {
            case BASKET_NOT_FOUND, OFFER_NOT_FOUND -> notFound(failure);
            case QUANTITY_NOT_POSITIVE, CURRENCY_MISMATCH, NOTHING_SELECTED, BLOCKED_BY_ITEMS, BASKET_INACTIVE,
                    OFFER_UNAVAILABLE -> unprocessable(failure);
            case CONCURRENT_MODIFICATION -> lostRace(failure, request);
            case OFFER_READ_UNAVAILABLE -> code(HttpStatus.SERVICE_UNAVAILABLE, failure);
            default -> unmapped(failure);
        };
    }

    /**
     * {@code 400} for a command record the constraints refused: one complaint per violated component, each naming the rule the
     * component states rather than the annotation that checks it (a message written on the component, never the default one).
     *
     * @param failure the binding result with the violated constraints
     * @return element 07's {@code errors[]}
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<Object> invalid(MethodArgumentNotValidException failure) {
        return malformed(failure.getBindingResult().getAllErrors().stream()
                .map(BasketErrorAdvice::complaintOf)
                .toList());
    }

    /**
     * {@code 400} for a body the command cannot take at all — a component it does not declare, a value it does not know, or
     * nothing that reads as JSON. An unknown component is refused rather than ignored ({@code http.rejection-status}): a typo in
     * a field name would otherwise drop the value the client meant to send, and the basket would answer {@code 200} to a
     * command it never received.
     *
     * @param failure the body that did not read, its cause naming the component where there is one
     * @return element 07's {@code errors[]}, one entry
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<Object> unreadable(HttpMessageNotReadableException failure) {
        return malformed(List.of(complaintOf(failure)));
    }

    /**
     * {@code 404}: the identity the path or the body named, so a client can tell which of its two arguments was wrong.
     */
    private static ResponseEntity<Object> notFound(ShoppingException failure) {
        String identity = namedIdentity(failure);
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                failure.error() == ShoppingException.Error.OFFER_NOT_FOUND
                        ? new Refusal(failure.error().name(), null, identity, null)
                        : new Refusal(failure.error().name(), identity, null, null));
    }

    private static ResponseEntity<Object> unprocessable(ShoppingException failure) {
        return failure.error() == ShoppingException.Error.BLOCKED_BY_ITEMS
                ? blockedHandover(failure, blockedRowsOf(failure))
                : code(HttpStatus.UNPROCESSABLE_CONTENT, failure);
    }

    /**
     * The refusal an operator has to be able to find afterwards: a customer could not complete the purchase, and the only
     * evidence was a {@code 422} carrying the rows that refused it.
     */
    private static ResponseEntity<Object> blockedHandover(ShoppingException failure, List<HandoverRefusal> rows) {
        log.warn("basket {} cannot be handed over, blocked by {}", namedIdentity(failure), offerIdsOf(rows));
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_CONTENT)
                .body(new Refusal(failure.error().name(), null, null, rows));
    }

    /**
     * The other refusal an operator has to find afterwards: a write that lost the race, which every route of this service can
     * answer — a {@code GET} included (D62) — and which the client is expected to retry. The route is what says which one.
     */
    private static ResponseEntity<Object> lostRace(ShoppingException failure, HttpServletRequest request) {
        log.warn("basket {} lost an optimistic lock answering {} {}", namedIdentity(failure), request.getMethod(),
                request.getRequestURI());
        return code(HttpStatus.CONFLICT, failure);
    }

    private static ResponseEntity<Object> malformed(List<Complaint> complaints) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new MalformedBody(complaints));
    }

    /**
     * An error the contract gives no status: rethrown, so the framework answers {@code 500} without a message. The one of them an
     * operator has a name for — the checkout slice turning a handover away (element 09 §Failure outcomes,
     * {@code HANDOVER_STALE}) — is stated here too, because a stack trace out of the container is not a line anybody finds, and
     * the refusal carries the basket and both revisions in its message.
     */
    private static ResponseEntity<Object> unmapped(ShoppingException failure) {
        if (failure.error() == ShoppingException.Error.HANDOVER_STALE) {
            log.warn("checkout turned a handover away, {}", failure.getMessage());
        }
        throw failure;
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

    private static List<String> offerIdsOf(List<HandoverRefusal> rows) {
        return rows.stream().map(HandoverRefusal::offerId).toList();
    }

    /**
     * One violated constraint as the wire reports it: the property path, and the rule the component states.
     *
     * <p>A rule about one component names that component. A rule about the relation between two of them — {@code scope item
     * names the offer it removes} — is a constraint on its own assertion, so the path it answers with is the assertion's name
     * ({@code offerNamed}, {@code subjectNamed}): the payload has no single component to blame, and naming one of the two would
     * send the client to the field it may have got right. A violation that names no property at all omits {@code field} rather
     * than reporting a Java type name.
     */
    private static Complaint complaintOf(ObjectError violation) {
        return new Complaint(violation instanceof FieldError field ? field.getField() : null,
                violation.getDefaultMessage());
    }

    /**
     * The one complaint a body that cannot be read produces: the component Jackson stopped at where it named one, and the rule
     * it broke otherwise — never the parser's own text, which says where in the body it stood, not what the client must change.
     */
    private static Complaint complaintOf(HttpMessageNotReadableException failure) {
        return failure.getCause() instanceof UnrecognizedPropertyException unknown
                ? new Complaint(unknown.getPropertyName(), unknown.getPropertyName() + NOT_A_COMPONENT)
                : new Complaint(propertyPathOf(failure.getCause()), UNREADABLE_COMMAND);
    }

    /** The dotted path of the value the body failed at — {@code items.offerId} — or {@code null} when it failed at the body. */
    private static String propertyPathOf(Throwable cause) {
        String path = !(cause instanceof MismatchedInputException malformed)
                ? ""
                : malformed.getPath().stream()
                        .map(JacksonException.Reference::getPropertyName)
                        .filter(Objects::nonNull)
                        .collect(Collectors.joining("."));
        return path.isEmpty() ? null : path;
    }

    /**
     * The one value a client branches on, the identity a {@code 404} names, and the rows that blocked a handover.
     *
     * @param error    element 07's code
     * @param basketId the basket a {@code 404} did not find
     * @param offerId  the offer a {@code 404} did not find
     * @param reasons  the rows a {@code BLOCKED_BY_ITEMS} names, {@code null} for every other code
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record Refusal(String error, String basketId, String offerId, List<HandoverRefusal> reasons) {
    }

    /**
     * Element 07's {@code 400} body: every component the command could not take, one entry each.
     *
     * @param errors one complaint per violated or undeclared component
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record MalformedBody(List<Complaint> errors) {
    }

    /**
     * One component the command could not take.
     *
     * @param field   the property path the client points at, absent when the body was not a JSON object at all
     * @param message the rule the component states, never the annotation's own text and never the body that was sent
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record Complaint(String field, String message) {
    }
}
