package shop.shopping.basket;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

/**
 * {@code PATCH /baskets/{basketId}/selection} — steps 12, 13, 15 and 17 of the contract: the three checkboxes of the screen.
 *
 * <p>Selection is state and not history (RULE-10), so the command records no event and the answer is the basket the new
 * selection prices (VIEW-1): ship it, the shipment's rows change the totals; ship it for the whole basket, every row does.
 * Deselecting everything is a valid basket with an empty selection — the refusal lives at the handover, not here (RULE-9).
 */
@RestController
@RequiredArgsConstructor
class BasketSelectionController {

    private final BasketService baskets;

    @PatchMapping(path = "/baskets/{basketId}/selection", consumes = APPLICATION_JSON_VALUE,
            produces = APPLICATION_JSON_VALUE)
    BasketContent select(@PathVariable String basketId, @RequestBody @Valid SelectItems command,
            @AuthenticationPrincipal Jwt caller) {
        return baskets.updateSelection(new BasketId(basketId), command.asSelection(), command.selected(),
                channelOf(caller));
    }

    private static Channel channelOf(Jwt caller) {
        return TokenChannel.claimedAs(caller.getClaimAsString(TokenChannel.CLAIM));
    }

    /**
     * The selection command: which rows it covers and whether it switches them on or off.
     *
     * <p>{@code seller} is the subject of {@code scope: shipment} — the payload groups a shipment by its seller, so that is the
     * identity the client already holds (element 01 §Data shown). It is required exactly when the scope names a shipment, and the
     * offer exactly when it names an item.
     *
     * @param scope    one item, one seller's shipment, or the whole basket
     * @param offerId  the offer, required exactly when the scope names one
     * @param seller   the seller, required exactly when the scope names a shipment
     * @param selected true to switch the rows on, false to switch them off
     */
    record SelectItems(@NotNull Scope scope, String offerId, String seller, @NotNull Boolean selected) {

        BasketSelection asSelection() {
            return switch (scope) {
                case item -> new BasketSelection.Item(offerId);
                case shipment -> new BasketSelection.Shipment(seller);
                case all -> new BasketSelection.All();
            };
        }

        /**
         * The identity a scope cannot do without. A missing scope is not this rule's case: the {@code @NotNull} on the component
         * refuses it as the malformed request it is, and a validation rule that switches on {@code null} would answer it with a
         * {@code 500} instead.
         */
        @AssertTrue(message = "scope item names an offer and scope shipment names a seller")
        boolean isSubjectNamed() {
            return scope == null || switch (scope) {
                case item -> offerId != null;
                case shipment -> seller != null;
                case all -> true;
            };
        }
    }

    /** Element 07's three selection scopes. */
    enum Scope {
        item,
        shipment,
        all
    }
}
