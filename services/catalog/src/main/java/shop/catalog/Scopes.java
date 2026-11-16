package shop.catalog;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The four scopes of 12 and the two authorization decisions a route rule cannot express: inside `PATCH /offers/{offerId}/draft` the
 * author owns {@code description} and {@code availabilityStart} while the photographer owns {@code media} (D13a), and on
 * `PATCH /offers/{offerId}/review` the author may only withdraw while every decision belongs to the reviewer (12's "withdrawal:
 * catalog-author, own request only").
 *
 * <p>Anything outside either set is `403 FIELD_NOT_IN_SCOPE`. Route-level scope stays in {@link AppConfiguration#filterChain}: this
 * class only answers which components and which decisions a set of scopes owns, so it never decides a route. Without D13a the
 * photographer could rewrite the description through the same endpoint; without the review rule the author could accept a review and
 * publish it.
 */
public final class Scopes {

    public static final String AUTHOR = "SCOPE_catalog-author";
    public static final String REVIEWER = "SCOPE_catalog-reviewer";
    public static final String PHOTOGRAPHER = "SCOPE_catalog-photographer";
    public static final String SALES = "SCOPE_catalog-sales";

    public static final String DESCRIPTION = "description";
    public static final String MEDIA = "media";
    public static final String AVAILABILITY_START = "availabilityStart";

    public static final String ACCEPTED = "accepted";
    public static final String REJECTED = "rejected";
    public static final String WITHDRAWN = "withdrawn";

    public static final String FIELD_NOT_IN_SCOPE = "FIELD_NOT_IN_SCOPE";

    private static final Map<String, Set<String>> DRAFT_COMPONENTS = Map.of(
            AUTHOR, Set.of(DESCRIPTION, AVAILABILITY_START),
            PHOTOGRAPHER, Set.of(MEDIA));

    private Scopes() {
    }

    /** D13a for the caller the request authenticated as: the draft controller hands it the components the body carried. */
    public static void requireDraftWrite(Collection<String> components) {
        requireDraftWrite(grantedScopes(), components);
    }

    static void requireDraftWrite(Set<String> grantedScopes, Collection<String> components) {
        Set<String> owned = ownedComponents(grantedScopes);
        refuse(components.stream().filter(component -> !owned.contains(component)).toList());
    }

    /** 12's review rule for the caller the request authenticated as: only the reviewer decides, the author only withdraws. */
    public static void requireReviewDecision(String decision) {
        requireReviewDecision(grantedScopes(), decision);
    }

    static void requireReviewDecision(Set<String> grantedScopes, String decision) {
        refuse(grantedScopes.contains(REVIEWER) || WITHDRAWN.equals(decision)
                ? List.of()
                : List.of(String.valueOf(decision)));
    }

    private static void refuse(Collection<String> outsideScope) {
        if (!outsideScope.isEmpty()) {
            throw new OutOfScopeException(outsideScope);
        }
    }

    private static Set<String> ownedComponents(Set<String> grantedScopes) {
        return grantedScopes.stream()
                .map(DRAFT_COMPONENTS::get)
                .filter(Objects::nonNull)
                .flatMap(Set::stream)
                .collect(Collectors.toUnmodifiableSet());
    }

    private static Set<String> grantedScopes() {
        Authentication caller = SecurityContextHolder.getContext().getAuthentication();
        return caller == null
                ? Set.of()
                : caller.getAuthorities().stream()
                        .map(GrantedAuthority::getAuthority)
                        .collect(Collectors.toUnmodifiableSet());
    }

    /** A component or a decision outside the caller's scope; the advice renders it as `403 FIELD_NOT_IN_SCOPE` and nothing else. */
    static final class OutOfScopeException extends RuntimeException {

        enum Error {
            OUT_OF_SCOPE("%s is outside the scopes of this caller (D13a, 12)");

            private final String template;

            Error(String template) {
                this.template = template;
            }

            String format(Object... details) {
                return template.formatted(details);
            }
        }

        OutOfScopeException(Collection<String> outsideScope) {
            super(Error.OUT_OF_SCOPE.format(outsideScope));
        }
    }
}
