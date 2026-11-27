package shop.shopping.basket;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import shop.shopping.FixedClockFixture;
import shop.shopping.HttpFixture;
import shop.shopping.IntegrationTest;
import shop.shopping.JsonAssert;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static shop.shopping.basket.BasketFixture.FIRST_OFFER;
import static shop.shopping.basket.BasketFixture.SECOND_OFFER;

/**
 * {@code PATCH /baskets/{basketId}/checkout} over the real chain: the handover keyed by the basket (step 18), the repeat that is
 * the same handover while the content is unchanged (step 19, HAND-4), and the two refusals the contract states — nothing selected
 * (step 14) and a row that blocks it (steps 16 and 21, D33).
 *
 * <p>The answer is the shape of record and not the payload that crosses: {@code selection} is the offer ids, {@code checkoutId}
 * stays inside the service and {@code deliveryEstimate} is not published (D41).
 */
@IntegrationTest
@Import(FixedClockFixture.class)
@TestPropertySource(properties = {"basket.delivery.rate=9990", "basket.delivery.free-when-smart=true"})
class BasketCheckoutControllerTest {

    @Autowired
    WebApplicationContext context;
    @Autowired
    OfferCatalog offers;

    FixtureOfferCatalog catalog;
    MockMvc mvc;
    String customer;
    String basketId;

    @BeforeEach
    void buildsTheBasketOfOneCustomer() throws Exception {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        catalog = (FixtureOfferCatalog) offers;
        catalog.failingWith(FixtureOfferCatalog.Failure.none);
        catalog.sells(FIRST_OFFER, 5);
        catalog.sells(SECOND_OFFER, 5);
        customer = HttpFixture.aCustomer();
        basketId = (String) HttpFixture.json(
                HttpFixture.asCustomer(mvc, customer, get("/baskets")), "$.baskets[0].basketId");
    }

    @Test
    void handsTheSelectionOverAndAnswersTheHandoverKeyedByTheBasket() throws Exception {
        addToBasket(FIRST_OFFER);

        MockHttpServletResponse response = handOver();

        assertThat(response.getStatus()).isEqualTo(200);
        JsonAssert.assertThat(HttpFixture.body(response)).isExactlyLike("""
                {
                  "handoverId": "%s",
                  "basketId": "%s",
                  "createdAt": "2026-11-26T10:00:00Z",
                  "customerId": "%s",
                  "currency": "PLN",
                  "selection": ["OF-1001"],
                  "revision": 2,
                  "totals": {
                    "productValue": { "amount": 99890, "currency": "PLN", "kind": "charge" },
                    "discountValue": { "amount": 10000, "currency": "PLN", "kind": "discount" },
                    "delivery": { "amount": 9990, "currency": "PLN", "kind": "charge" },
                    "totalWithDelivery": { "amount": 109880, "currency": "PLN", "kind": "charge" }
                  }
                }
                """, basketId, basketId, customer);
    }

    @Test
    void answersTheSameHandoverWhileTheContentIsUnchanged() throws Exception {
        addToBasket(FIRST_OFFER);

        assertThat(HttpFixture.body(handOver())).isEqualTo(HttpFixture.body(handOver()));
    }

    @Test
    void neverClearsTheBasketByHandingItOver() throws Exception {
        addToBasket(FIRST_OFFER);
        handOver();

        assertThat((Iterable<?>) HttpFixture.json(
                HttpFixture.asCustomer(mvc, customer, get("/baskets/{basketId}", basketId)), "$.items")).hasSize(1);
    }

    @Test
    void refusesAHandoverOfNothingSelected() throws Exception {
        addToBasket(FIRST_OFFER);
        HttpFixture.asCustomer(mvc, customer, patch("/baskets/{basketId}/selection", basketId)
                .contentType(APPLICATION_JSON).content("{\"scope\": \"all\", \"selected\": false}"));

        MockHttpServletResponse response = handOver();

        assertThat(response.getStatus()).isEqualTo(422);
        JsonAssert.assertThat(HttpFixture.body(response)).isExactlyLike("""
                { "error": "NOTHING_SELECTED" }
                """);
    }

    @Test
    void refusesAHandoverBlockedByASelectedRowAndNamesIt() throws Exception {
        catalog.sells(SECOND_OFFER, 0);
        addToBasket(FIRST_OFFER);
        addToBasket(SECOND_OFFER);

        MockHttpServletResponse response = handOver();

        assertThat(response.getStatus()).isEqualTo(422);
        JsonAssert.assertThat(HttpFixture.body(response)).isExactlyLike("""
                {
                  "error": "BLOCKED_BY_ITEMS",
                  "reasons": [ { "offerId": "OF-1002", "status": "outOfStock", "stale": false } ]
                }
                """);
    }

    @Test
    void refusesAHandoverOfABasketThePathDoesNotName() throws Exception {
        MockHttpServletResponse response = HttpFixture.asCustomer(mvc, customer,
                patch("/baskets/{basketId}/checkout", "B-0000"));

        assertThat(response.getStatus()).isEqualTo(404);
        JsonAssert.assertThat(HttpFixture.body(response)).isExactlyLike("""
                { "error": "BASKET_NOT_FOUND", "basketId": "B-0000" }
                """);
    }

    @Test
    void refusesTheHandoverWithoutAToken() throws Exception {
        MockHttpServletResponse response = HttpFixture.anonymous(mvc,
                patch("/baskets/{basketId}/checkout", basketId));

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(HttpFixture.body(response)).isEmpty();
    }

    @Test
    void refusesTheHandoverToTheServiceToken() throws Exception {
        MockHttpServletResponse response = HttpFixture.asService(mvc, patch("/baskets/{basketId}/checkout", basketId));

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(HttpFixture.body(response)).isEmpty();
    }

    private void addToBasket(String offerId) throws Exception {
        HttpFixture.asCustomer(mvc, customer, patch("/baskets/{basketId}/items/{offerId}", basketId, offerId)
                .contentType(APPLICATION_JSON).content("{\"quantity\": 1}"));
    }

    private MockHttpServletResponse handOver() throws Exception {
        return HttpFixture.asCustomer(mvc, customer, patch("/baskets/{basketId}/checkout", basketId));
    }
}
