package shop.catalog;

import java.util.List;

/**
 * A well-formed command the state of an aggregate refuses — 12's one body-carrying `409`.
 *
 * <p>Every bounded context that can refuse a command implements it on its single exception and answers with the name of its
 * {@code Error} constant, so the advice maps a status and a {@code reason} the client can branch on while the message stays a log
 * line ({@code http.rejection-status}, {@code error-handling.md}). It carries no status, no body and no transport type: the refusal
 * is the vocabulary, the mapping is {@link ApiErrorAdvice}'s.
 *
 * <p>A context whose failures are not command refusals does not implement it: 08's configuration errors arrive before any draft is
 * evaluated, so they stay an unexpected `500` instead of a `reason` no client can act on.
 */
public interface DomainRefusal {

    String reason();

    /** The blocking findings a `409` carries beside its reason (12's `findings?`), empty when the refusal names none. */
    default List<?> findings() {
        return List.of();
    }
}
