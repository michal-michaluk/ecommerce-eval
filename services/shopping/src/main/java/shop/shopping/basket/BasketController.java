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
 * <p>It is one of the routes that pull and write (D66, D62): it re-reads the basket's offers, applies each answer through the
 * ingestion path and stores what changed, so two concurrent answers to one basket can report
 * {@code 409 CONCURRENT_MODIFICATION} — a retryable answer the client owns. It therefore holds the write model, which is one of
 * element 07 §Recorded deviations, and not the projection. A basket owned by another customer answers {@code 404}, exactly as an
 * unknown id does (D68).
 */
@RestController
@RequiredArgsConstructor
class BasketController {

    private final BasketService baskets;

    @GetMapping(path = "/baskets/{basketId}", produces = APPLICATION_JSON_VALUE)
    BasketContent read(@PathVariable String basketId, @AuthenticationPrincipal Jwt caller) {
        return baskets.render(new BasketId(basketId), callerOf(caller));
    }

    private static Caller callerOf(Jwt caller) {
        return Caller.of(caller.getSubject(), caller.getClaimAsString(Caller.CHANNEL_CLAIM));
    }
}
