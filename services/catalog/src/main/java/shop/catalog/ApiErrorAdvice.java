package shop.catalog;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.exc.UnrecognizedPropertyException;

import java.util.List;

/**
 * The one advice that owns 12's adapter-side rejections ({@code http.error-handler-single}, {@code http.rejection-status}): a refused
 * command becomes `409 {reason, findings?}`, an unknown offer a `404` with no body, a draft component or a review decision outside the
 * caller's scope `403 {reason}`, an invalid or unreadable command — or a paging parameter that is missing, mistyped or outside D16's
 * bound — `400 {errors[]}`, and a lost optimistic lock `409 {retry}`.
 *
 * <p>No seventh: the `401` and the missing-scope `403` are the framework's, the empty `403` body comes from the entry point in
 * {@link AppConfiguration}, `406`/`415` stay the framework's, and everything that is no refusal is rethrown so the framework answers
 * `500` — no body ever carries a message or a stack trace ({@code server.error.include-message: never}).
 *
 * <p>The refusal handler declares {@link RuntimeException} because {@link DomainRefusal} is an interface and {@code @ExceptionHandler}
 * accepts only {@code Throwable} types; 12 splits the class further, by reason, into the one `404` the domain can raise and the `409`
 * of a state that forbids the command.
 */
@RestControllerAdvice
class ApiErrorAdvice {

    static final String UNKNOWN_OFFER = "OFFER_UNKNOWN";

    @ExceptionHandler(RuntimeException.class)
    ResponseEntity<Object> refused(RuntimeException failure) {
        return failure instanceof DomainRefusal refusal ? refusalResponse(refusal) : rethrow(failure);
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    Retry lostRace() {
        return new Retry(true);
    }

    @ExceptionHandler(Scopes.OutOfScopeException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    Refusal outsideScope() {
        return new Refusal(Scopes.FIELD_NOT_IN_SCOPE, List.of());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    Rejected invalidCommand(MethodArgumentNotValidException invalid) {
        return new Rejected(invalid.getFieldErrors().stream().map(ApiErrorAdvice::field).toList());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    Rejected unreadableCommand(HttpMessageNotReadableException unreadable) {
        return new Rejected(unknownFields(unreadable.getMostSpecificCause()));
    }

    /**
     * D16's bound is a constraint of the request like any other, so a page or a size outside it answers the `400 {errors[{field}]}`
     * the invalid commands answer — never an empty body beside a status another failure class already owns a shape for.
     */
    @ExceptionHandler(HandlerMethodValidationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    Rejected invalidParameter(HandlerMethodValidationException invalid) {
        return new Rejected(invalid.getParameterValidationResults().stream()
                .map(result -> new Field(result.getMethodParameter().getParameterName()))
                .toList());
    }

    /** D16: a collection without its `page` or `size` answers the same `400 {errors[{field}]}` and names the one left out. */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    Rejected missingParameter(MissingServletRequestParameterException missing) {
        return oneField(missing.getParameterName());
    }

    /** A parameter that is there and is not the number the route takes is the same refusal, named the same way. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    Rejected mistypedParameter(MethodArgumentTypeMismatchException mistyped) {
        return oneField(mistyped.getName());
    }

    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<Object> raisedByTheHandler(ResponseStatusException raised) {
        return ResponseEntity.status(raised.getStatusCode()).build();
    }

    private static ResponseEntity<Object> refusalResponse(DomainRefusal refusal) {
        return UNKNOWN_OFFER.equals(refusal.reason())
                ? ResponseEntity.status(HttpStatus.NOT_FOUND).build()
                : ResponseEntity.status(HttpStatus.CONFLICT).body(new Refusal(refusal.reason(), refusal.findings()));
    }

    private static ResponseEntity<Object> rethrow(RuntimeException failure) {
        throw failure;
    }

    private static Field field(FieldError invalid) {
        return new Field(invalid.getField());
    }

    private static Rejected oneField(String parameter) {
        return new Rejected(List.of(new Field(parameter)));
    }

    private static List<Field> unknownFields(Throwable cause) {
        return cause instanceof UnrecognizedPropertyException unknown
                ? List.of(new Field(unknown.getPropertyName()))
                : List.of();
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    record Refusal(String reason, List<?> findings) {
    }

    record Retry(boolean retry) {
    }

    record Rejected(List<Field> errors) {
    }

    record Field(String field) {
    }
}
