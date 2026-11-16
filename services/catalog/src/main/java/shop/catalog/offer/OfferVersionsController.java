package shop.catalog.offer;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import shop.catalog.CreatedResource;
import tools.jackson.databind.exc.UnrecognizedPropertyException;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

/**
 * 12's publish history (D31): the frozen versions of one offer, in the order they were published, and the revert that appends the
 * next one from an older one (D19).
 *
 * <p>A revert is a create, not an edit — the source version is copied and never reused (RULE-5) — so it answers `201` with the version
 * it froze and names that version in {@code Location}. The two facts a version carries without the command are the transport's:
 * {@code publishedAt} from the injected {@link Clock} (D35) and {@code publishedBy} from the authenticated reviewer.
 *
 * <p>A source version the offer does not have is a resource that does not exist, so the handler refuses it as the `404` it raises for
 * an unknown offer; only a state that forbids the command reaches the domain's `409` (RULE-5, {@code error-handling.md}).
 */
@RestController
@RequiredArgsConstructor
class OfferVersionsController {

    private static final int MAX_PAGE_SIZE = 100;
    private static final int FIRST_VERSION = 1;

    private final OfferService offers;
    private final Clock clock;

    @GetMapping(path = "/offers/{offerId}/versions", params = {"page", "size"}, produces = APPLICATION_JSON_VALUE)
    VersionsPage versions(@PathVariable String offerId, @RequestParam int page, @RequestParam int size) {
        List<OfferVersion> versions = offers.versionsOf(offerId).orElseThrow(OfferVersionsController::offerUnknown);
        refusePageOutsideItsBounds(page, size);
        return VersionsPage.of(versions, page, size);
    }

    @PostMapping(path = "/offers/{offerId}/versions", consumes = APPLICATION_JSON_VALUE, produces = APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    RevertedVersion revert(@PathVariable String offerId,
                           @RequestBody @Valid RevertVersion revert,
                           Authentication reviewer) {
        refuseUnknown(declared(revert.unknown()));
        int frozen = offers.versionsOf(offerId).orElseThrow(OfferVersionsController::offerUnknown).size();
        if (revert.copyFrom() < FIRST_VERSION || revert.copyFrom() > frozen) {
            throw offerUnknown();
        }
        return offers.revert(offerId, revert.copyFrom(), Instant.now(clock), reviewer.getName())
                .map(offer -> RevertedVersion.of(offer, revert.copyFrom()))
                .orElseThrow(OfferVersionsController::offerUnknown);
    }

    private static ResponseStatusException offerUnknown() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND);
    }

    /** The members a command collected as "not mine": a body that carried none leaves the trap empty, or absent. */
    private static Collection<String> declared(Map<String, Object> unknown) {
        return unknown == null ? List.of() : unknown.keySet();
    }

    /** D18: a revert names its source version and nothing else, so another key is a `400`, never a silently dropped choice. */
    private static void refuseUnknown(Collection<String> unknown) {
        if (!unknown.isEmpty()) {
            throw unknownComponent(unknown.iterator().next());
        }
    }

    /**
     * 12 names the offending field beside the `400`, and the one advice reads that name off a Jackson
     * {@link UnrecognizedPropertyException}. The process-wide mapper ignores unknown members on purpose, so the command collects them
     * and the adapter states the key the refusal names.
     */
    private static HttpMessageNotReadableException unknownComponent(String component) {
        return new HttpMessageNotReadableException("unknown component " + component,
                new UnrecognizedPropertyException(null, component, null, Object.class, component, List.of()), null);
    }

    /** D16: both parameters are required, {@code page >= 0} and {@code 1 <= size <= 100}, and anything else is a `400`. */
    private static void refusePageOutsideItsBounds(int page, int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        }
    }

    /** D19: a revert names the version it copies and nothing else — the new number and the publisher are not a client's to choose. */
    record RevertVersion(@NotNull Integer copyFrom, @JsonAnySetter Map<String, Object> unknown) {
    }

    /** One frozen version as 12 publishes it: its number, who published it and when, and the version it was copied from (D31). */
    record OfferVersionView(int offerVersion, Instant publishedAt, String publishedBy, Integer copiedFrom,
                            LocalDate availabilityStart) {

        static OfferVersionView of(OfferVersion version) {
            return new OfferVersionView(version.number(), version.publishedAt(), version.publishedBy(),
                    version.copiedFrom(), version.availabilityStart());
        }
    }

    /** The publish history is a collection like every other one: `items` plus the page it came from (D16). */
    record VersionsPage(List<OfferVersionView> items, Paging page) {

        static VersionsPage of(List<OfferVersion> versions, int page, int size) {
            List<OfferVersionView> all = versions.stream().map(OfferVersionView::of).toList();
            int from = Math.min(page * size, all.size());
            List<OfferVersionView> items = all.subList(from, Math.min(from + size, all.size()));
            return new VersionsPage(items, new Paging(page, size, all.size()));
        }

        record Paging(int number, int size, long totalElements) {
        }
    }

    /**
     * D19's answer: the version a revert froze, at the path that identifies it — the created sub-resource of the history, never the
     * offer it now serves (D19a's `Location`).
     */
    record RevertedVersion(String offerId, String offerState, int offerVersion, int copiedFrom) implements CreatedResource {

        static RevertedVersion of(Offer offer, int copyFrom) {
            return new RevertedVersion(offer.offerId(), OfferController.OfferView.stateName(offer.state()),
                    offer.version(), copyFrom);
        }

        @Override
        public String location() {
            return "/offers/" + offerId + "/versions/" + offerVersion;
        }
    }
}
