package shop.shopping.basket;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

/**
 * {@code GET /baskets/{basketId}} — step 11 of the contract: content, totals and the delivery estimate in one call (D10).
 *
 * <p>It is the one read that writes (D62): the render pulls the basket's offers through the catalog, applies each answer
 * through the ingestion path and stores before it computes, so two concurrent renders of one basket can answer
 * {@code 409 CONCURRENT_MODIFICATION} — a retryable answer the client owns. It therefore holds the write model, which is the
 * third recorded deviation of element 07 §Recorded deviations, and not the projection.
 */
@RestController
@RequiredArgsConstructor
class BasketController {

    private final BasketService baskets;

    @GetMapping(path = "/baskets/{basketId}", produces = APPLICATION_JSON_VALUE)
    BasketContent read(@PathVariable String basketId, @AuthenticationPrincipal Jwt caller) {
        return baskets.render(new BasketId(basketId), channelOf(caller));
    }

    private static Channel channelOf(Jwt caller) {
        return TokenChannel.claimedAs(caller.getClaimAsString(TokenChannel.CLAIM));
    }
}
