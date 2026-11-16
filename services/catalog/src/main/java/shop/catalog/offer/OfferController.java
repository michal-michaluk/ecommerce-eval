package shop.catalog.offer;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import shop.catalog.CreatedResource;
import tools.jackson.databind.exc.UnrecognizedPropertyException;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

/**
 * 12's offer resource: the create, the paged list, the state read and the removal — no verb segments, no `/api` prefix, and no
 * business decision: every handler validates the command, calls {@link OfferService} once and maps the snapshot back (D13b, D14).
 *
 * <p>The body is the aggregate snapshot, which is the only truth this resource has: element 17 gives it no read model, so there is
 * nothing else to serve it from (D14, {@code http.no-write-model-in-read} has no alternative here).
 *
 * <p>{@code offerVersion} is absent while the offer has published nothing and the number of the version in force afterwards, and
 * {@code offerId} is the one the context minted through its primary port (D31), never a client value.
 */
@RestController
@RequiredArgsConstructor
class OfferController {

    private static final String OFFERS = "/offers";
    private static final int MAX_PAGE_SIZE = 100;

    private final OfferService offers;

    @PostMapping(path = "/offers", consumes = APPLICATION_JSON_VALUE, produces = APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    OfferView create(@RequestBody @Valid CreateOffer create) {
        refuseUnknown(declared(create.unknown()));
        return OfferView.of(offers.draftOffer(create.toDraft()));
    }

    /** D16: both parameters are required, {@code page >= 0} and {@code 1 <= size <= 100}, and anything else is a `400`. */
    @GetMapping(path = "/offers", params = {"page", "size"}, produces = APPLICATION_JSON_VALUE)
    OffersPage list(@RequestParam int page, @RequestParam int size) {
        refusePageOutsideItsBounds(page, size);
        return OffersPage.of(offers.offers(page, size), page, size, offers.countOffers());
    }

    @GetMapping(path = "/offers/{offerId}", produces = APPLICATION_JSON_VALUE)
    OfferView offer(@PathVariable String offerId) {
        return offers.getOffer(offerId).map(OfferView::of).orElseThrow(OfferController::offerUnknown);
    }

    @PatchMapping(path = "/offers/{offerId}", consumes = APPLICATION_JSON_VALUE, produces = APPLICATION_JSON_VALUE)
    OfferView patchOffer(@PathVariable String offerId, @RequestBody @Valid RemoveOffer removal) {
        refuseUnknown(declared(removal.unknown()));
        return offers.removeOffer(offerId).map(OfferView::of).orElseThrow(OfferController::offerUnknown);
    }

    private static ResponseStatusException offerUnknown() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND);
    }

    /** The members a command collected as "not mine": a body that carried none leaves the trap empty, or absent. */
    private static Collection<String> declared(Map<String, Object> unknown) {
        return unknown == null ? List.of() : unknown.keySet();
    }

    /**
     * D18: `PATCH` means these components and nothing else, so a key the command does not declare is a `400` — the same refusal, in the
     * same shape (`errors[{field}]`), that a malformed body gets.
     */
    private static void refuseUnknown(Collection<String> unknown) {
        if (!unknown.isEmpty()) {
            throw unknownComponent(unknown.iterator().next());
        }
    }

    /**
     * 12 names the offending field beside the `400`, and the one advice reads that name off a Jackson
     * {@link UnrecognizedPropertyException}. The process-wide mapper ignores unknown members on purpose (a published payload stays
     * readable across versions), so the command collects them and the adapter states the key the refusal names.
     */
    private static HttpMessageNotReadableException unknownComponent(String component) {
        return new HttpMessageNotReadableException("unknown component " + component,
                new UnrecognizedPropertyException(null, component, null, Object.class, component, List.of()), null);
    }

    private static void refusePageOutsideItsBounds(int page, int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        }
    }

    /**
     * 12's create body: the title and the identifiers the draft opens with (01 Behaviour) plus the category path the listing is
     * routed by. An unknown component is `400` like any other command (D18), so a typo never silently drops a value.
     *
     * <p>The check digit is a constraint of the command and not of the draft: a malformed identifier is a broken request (`400
     * {errors[{field: eanGtin}]}`), while a draft that carries none is a legal draft (RULE-8).
     */
    record CreateOffer(@NotBlank String title, @NotBlank String productNumber, String eanGtin, List<String> categoryPath,
                       @JsonAnySetter Map<String, Object> unknown) {

        @AssertTrue(message = "eanGtin must be 13 digits with a valid GTIN-13 check digit")
        boolean isEanGtin() {
            return new ProductDescription.Identifiers(productNumber, eanGtin, null).hasValidEanGtin();
        }

        ProductDescription.Draft toDraft() {
            ProductDescription.Identifiers identifiers = new ProductDescription.Identifiers(productNumber, eanGtin, null);
            return ProductDescription.Draft.blank(title, identifiers)
                    .merge(ProductDescription.Draft.builder().categoryPath(categoryPath).build());
        }
    }

    /**
     * D13b: removal is an edit of the offer's own state, never of its content — and `removed` is the only state a client may name,
     * so any other value is a `400` instead of a silent no-op.
     */
    record RemoveOffer(@NotNull @Pattern(regexp = "removed") String offerState,
                       @JsonAnySetter Map<String, Object> unknown) {
    }

    /** The offer resource of 12: state, current version, availability — the create names its own location (D19a). */
    record OfferView(String offerId, String offerState, Integer offerVersion, LocalDate availabilityStart)
            implements CreatedResource {

        static OfferView of(Offer offer) {
            return new OfferView(offer.offerId(), stateName(offer.state()),
                    offer.version() == 0 ? null : offer.version(), offer.availabilityStart());
        }

        /** 02's lifecycle in 12's wire spelling; `pending_review` would name an enum constant, not the state a client branches on. */
        static String stateName(Offer.State state) {
            return switch (state) {
                case DRAFT -> "draft";
                case PENDING_REVIEW -> "pendingReview";
                case PUBLISHED -> "published";
                case REMOVED -> "removed";
            };
        }

        @Override
        public String location() {
            return OFFERS + "/" + offerId;
        }
    }

    /** A collection is always `items` plus the page it came from, so a client never guesses how much it is missing (D16). */
    record OffersPage(List<OfferView> items, Paging page) {

        static OffersPage of(List<Offer> offers, int page, int size, long totalElements) {
            return new OffersPage(offers.stream().map(OfferView::of).toList(), new Paging(page, size, totalElements));
        }

        record Paging(int number, int size, long totalElements) {
        }
    }
}
