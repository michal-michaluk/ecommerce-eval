package shop.shopping;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import shop.shopping.kernel.Availability;
import shop.shopping.kernel.HandoverFixture;
import shop.shopping.kernel.ShoppingException;
import shop.shopping.kernel.ShoppingException.Error;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static shop.shopping.basket.BasketFixture.aRefusalOf;

/**
 * Element 07 §Error contract as one table: every code the service can raise answers the status the contract gives it, and every
 * body carries the code a client branches on and never the message.
 *
 * <p>The two codes a fixture-driven HTTP test cannot produce are pinned here and nowhere else — {@code CURRENCY_MISMATCH}, because
 * the increment's catalog sells in one currency only, and {@code CONCURRENT_MODIFICATION}, because a single-threaded test cannot
 * lose a race. Anything the contract does not name is rethrown, which is what makes the framework answer {@code 500} without a
 * message.
 */
class BasketErrorAdviceTest {

    final BasketErrorAdvice advice = new BasketErrorAdvice();

    @Test
    void answers404WithTheIdentityTheCallerNamed() {
        assertRefused(Error.BASKET_NOT_FOUND, 404, """
                { "error": "BASKET_NOT_FOUND", "basketId": "B-7f3a" }
                """, "B-7f3a");
        assertRefused(Error.OFFER_NOT_FOUND, 404, """
                { "error": "OFFER_NOT_FOUND", "offerId": "OF-0000" }
                """, "OF-0000");
    }

    @Test
    void answersEveryRefusedCommandWith422AndItsCode() {
        assertRefused(Error.QUANTITY_NOT_POSITIVE, 422, """
                { "error": "QUANTITY_NOT_POSITIVE" }
                """, 0);
        assertRefused(Error.CURRENCY_MISMATCH, 422, """
                { "error": "CURRENCY_MISMATCH" }
                """, "EUR", "PLN");
        assertRefused(Error.OFFER_UNAVAILABLE, 422, """
                { "error": "OFFER_UNAVAILABLE" }
                """, "OF-2007");
        assertRefused(Error.NOTHING_SELECTED, 422, """
                { "error": "NOTHING_SELECTED" }
                """, "B-7f3a");
        assertRefused(Error.BASKET_INACTIVE, 422, """
                { "error": "BASKET_INACTIVE" }
                """, "B-7f3a", "archived");
    }

    @Test
    void namesTheRowsThatBlockedTheHandoverAsAnArrayOfObjects() {
        assertRefused(Error.BLOCKED_BY_ITEMS, 422, """
                {
                  "error": "BLOCKED_BY_ITEMS",
                  "reasons": [ { "offerId": "OF-1002", "status": "outOfStock", "stale": false } ]
                }
                """, "B-7f3a", List.of(HandoverFixture.aRefusal("OF-1002", Availability.Status.outOfStock, false)));
    }

    @Test
    void answersAWriteThatLostTheRaceWithARetryable409() {
        assertRefused(Error.CONCURRENT_MODIFICATION, 409, """
                { "error": "CONCURRENT_MODIFICATION" }
                """, "B-7f3a");
    }

    @Test
    void answersAnOfferNobodyCouldReadWith503() {
        assertRefused(Error.OFFER_READ_UNAVAILABLE, 503, """
                { "error": "OFFER_READ_UNAVAILABLE" }
                """, "OF-1001");
    }

    @Test
    void rethrowsACodeTheContractDoesNotNameSoTheFrameworkAnswers500() {
        assertThatThrownBy(() -> advice.refused(aRefusalOf(Error.AMOUNT_NEGATIVE, -1)))
                .isInstanceOfSatisfying(ShoppingException.class,
                        refused -> assertThat(refused.error()).isEqualTo(Error.AMOUNT_NEGATIVE));
    }

    private void assertRefused(Error error, int status, String body, Object... details) {
        ResponseEntity<Object> answer = advice.refused(aRefusalOf(error, details));
        assertThat(answer.getStatusCode().value()).isEqualTo(status);
        JsonAssert.assertThat(answer.getBody()).isExactlyLike(body);
    }
}
