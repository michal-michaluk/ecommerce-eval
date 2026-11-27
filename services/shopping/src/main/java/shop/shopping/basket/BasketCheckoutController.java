package shop.shopping.basket;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

/**
 * {@code PATCH /baskets/{basketId}/checkout} — steps 14, 16, 18, 19 and 21 of the contract: {@code go to checkout} is a command,
 * not a navigation, so it is a {@code PATCH} with no resource of its own (element 07 §Contract decisions, D32).
 *
 * <p>The answer is {@link HandoverAnswer} and not the payload that crosses: the client reads the offers that crossed, the four
 * totals and the identity of the handover, never checkout's own id (D41). The route pulls before it composes (D66), so what
 * crosses is the basket as the catalog describes it now, not as it was when the row was added. A basket that is blocked,
 * empty-handed or archived is refused by the aggregate and reaches the wire through the one advice — {@code 422
 * NOTHING_SELECTED}, {@code 422 BLOCKED_BY_ITEMS} with the rows that refuse it, {@code 422 BASKET_INACTIVE} — and a basket
 * owned by another customer is {@code 404 BASKET_NOT_FOUND} (D68), so no answer echoes its owner.
 */
@RestController
@RequiredArgsConstructor
class BasketCheckoutController {

    private final BasketService baskets;

    @PatchMapping(path = "/baskets/{basketId}/checkout", produces = APPLICATION_JSON_VALUE)
    HandoverAnswer handOver(@PathVariable String basketId, @RequestBody(required = false) @Valid HandOver handOver,
            @AuthenticationPrincipal Jwt caller) {
        return baskets.handOver(new BasketId(basketId), callerOf(caller));
    }

    private static Caller callerOf(Jwt caller) {
        return Caller.of(caller.getSubject(), caller.getClaimAsString(Caller.CHANNEL_CLAIM));
    }

    /**
     * The command's whole content: nothing. What crosses is the basket's current selection, which the client already decided
     * row by row on the selection route, so the body is read as present-or-absent and no value of it is a parameter
     * ({@code http.null-means-absent}).
     */
    record HandOver() {
    }
}
