package shop.shopping.basket;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Null;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

/**
 * The two routes of {@code /baskets/{basketId}/items}: one row's quantity, and every removal.
 *
 * <p>The split is deliberate (D28, F16): the path that carries an offer only ever sets its absolute quantity, so a retried
 * {@code PATCH …/items/OF-1001} cannot double a basket the way a relative change would, and an offer the basket does not hold is
 * added by the same command. The path that carries no offer is the only one that removes, and {@code quantity: null} is how it
 * says so — a number there would be a second way to change a quantity the other route already owns.
 *
 * <p>Both answer the whole basket (steps 3–10 and 22 of the contract), so the client never re-reads after a command — and both
 * pull before they answer (D66): the rows' offers are re-read, the answers applied through the ingestion path and the document
 * stored when anything changed, which is how a row's availability can move between two commands without an event transport. Every
 * refusal is the aggregate's and reaches the wire through the one advice: {@code 422 QUANTITY_NOT_POSITIVE},
 * {@code 422 CURRENCY_MISMATCH}, {@code 422 OFFER_UNAVAILABLE}, {@code 404 OFFER_NOT_FOUND}, {@code 404 BASKET_NOT_FOUND} — the
 * last one also for a basket owned by another customer (D68).
 */
@RestController
@RequiredArgsConstructor
class BasketItemsController {

    private final BasketService baskets;

    @PatchMapping(path = "/baskets/{basketId}/items/{offerId}", consumes = APPLICATION_JSON_VALUE,
            produces = APPLICATION_JSON_VALUE)
    BasketContent setQuantity(@PathVariable String basketId, @PathVariable String offerId,
            @RequestBody @Valid UpdateQuantity update, @AuthenticationPrincipal Jwt caller) {
        return baskets.setQuantity(new BasketId(basketId), offerId, update.quantity(), callerOf(caller));
    }

    @PatchMapping(path = "/baskets/{basketId}/items", consumes = APPLICATION_JSON_VALUE,
            produces = APPLICATION_JSON_VALUE)
    BasketContent remove(@PathVariable String basketId, @RequestBody @Valid RemoveItems removal,
            @AuthenticationPrincipal Jwt caller) {
        return baskets.remove(new BasketId(basketId), removal.scope(), removal.offerId(), callerOf(caller));
    }

    private static Caller callerOf(Jwt caller) {
        return Caller.of(caller.getSubject(), caller.getClaimAsString(Caller.CHANNEL_CLAIM));
    }

    /**
     * The absolute quantity one row is set to — at least one, because zero is not a quantity and removal has its own route
     * (RULE-3, D28).
     *
     * @param quantity the units the row should hold, never a change relative to what it held
     */
    record UpdateQuantity(
            @NotNull(message = "a quantity change names the quantity it sets, at least 1") Integer quantity) {
    }

    /**
     * Which rows a removal takes (element 07 §Contract decisions). The contract states the command as
     * {@code {"scope": …, "offerId": …, "quantity": null}}; the null is what removes, so this route refuses any
     * other quantity rather than ignoring it — a number here would be a second, silent way to change a quantity the item
     * route already owns, and a removal that quietly ignored it would remove what the client meant to keep (D28).
     *
     * @param scope    one row, the current selection, or the whole basket
     * @param offerId  the offer, required exactly when the scope names one
     * @param quantity always absent: {@code null} is the contract's way of saying "this command removes"
     */
    record RemoveItems(@NotNull(message = "a removal names its scope: item, selection or all") BasketRemoval scope,
            String offerId, @Null(message = "this route removes rows, it never changes a quantity") Integer quantity) {

        @AssertTrue(message = "scope item names the offer it removes")
        boolean isOfferNamed() {
            return scope != BasketRemoval.item || offerId != null;
        }
    }
}
