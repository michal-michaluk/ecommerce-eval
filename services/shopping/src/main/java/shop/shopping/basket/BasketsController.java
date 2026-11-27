package shop.shopping.basket;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

/**
 * {@code GET /baskets} — step 1 of the contract: the customer's active baskets, and never an empty list, because asking a
 * customer with none creates the one they always own (D29, D42, element 07 §Contract decisions).
 *
 * <p>The identity is the token's subject and never a value the client sends; the authority that gates the route is the chain's,
 * decided once in {@code AppConfiguration} (D27), and the row it answers is the customer's own — the route filters by the subject,
 * so no second customer can appear in a list. The answer is the read model's, so this adapter holds the projection ({@code
 * http.no-write-model-in-read}) — with the one write element 07 §Recorded deviations grants it, because a customer with no basket
 * gets one and the projection delegates that creation to the basket service rather than reaching the write model itself.
 */
@RestController
@RequiredArgsConstructor
class BasketsController {

    private final BasketsProjection baskets;

    @GetMapping(path = "/baskets", produces = APPLICATION_JSON_VALUE)
    Baskets list(@AuthenticationPrincipal Jwt caller) {
        return new Baskets(baskets.summariesOf(new CustomerId(caller.getSubject())));
    }

    /**
     * The one key the route answers with: the baskets, as their own rows hold them.
     *
     * @param baskets one row per active basket, the created one included
     */
    record Baskets(List<BasketSummary> baskets) {
    }
}
