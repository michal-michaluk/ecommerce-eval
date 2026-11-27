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

/**
 * {@code GET /baskets/{basketId}} over the real chain: content, totals and the estimate in one answer (D10), the pull on render
 * that makes this read a write (D62), and the two empty-bodied refusals of the matrix.
 *
 * <p>The delivery rate is the capture's {@code 9,99 zł} and the clock is {@link FixedClockFixture}'s instant, so the rendered
 * body is the one {@code prototypes/basket-api.prove.md} states — the only difference is the fixture catalog's seller and its
 * {@code 10,00 zł} flattened discount, which no golden prints.
 */
@IntegrationTest
@Import(FixedClockFixture.class)
@TestPropertySource(properties = {"basket.delivery.rate=9990", "basket.delivery.free-when-smart=true"})
class BasketControllerTest {

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
        customer = HttpFixture.aCustomer();
        basketId = (String) HttpFixture.json(
                HttpFixture.asCustomer(mvc, customer, get("/baskets")), "$.baskets[0].basketId");
    }

    @Test
    void rendersTheBasketWithItsItemItsShipmentItsTotalsAndItsEstimate() throws Exception {
        setQuantity(1);

        MockHttpServletResponse response = read();

        assertThat(response.getStatus()).isEqualTo(200);
        JsonAssert.assertThat(HttpFixture.body(response)).isExactlyLike("""
                {
                  "basketId": "%s",
                  "state": "active",
                  "currency": "PLN",
                  "items": [
                    {
                      "offerId": "OF-1001",
                      "seller": "FIXTURE_SELLER",
                      "title": "Fixture offer OF-1001",
                      "picture": "fixture-OF-1001",
                      "quantity": 1,
                      "selected": true,
                      "availability": { "status": "available", "stale": false },
                      "currentPrice": { "amount": 99890, "currency": "PLN", "kind": "charge" },
                      "discount": { "amount": 10000, "currency": "PLN", "kind": "discount" },
                      "lineTotal": { "amount": 99890, "currency": "PLN", "kind": "charge" }
                    }
                  ],
                  "shipments": [
                    {
                      "seller": "FIXTURE_SELLER",
                      "items": ["OF-1001"],
                      "productValue": { "amount": 99890, "currency": "PLN", "kind": "charge" },
                      "delivery": { "amount": 9990, "currency": "PLN", "kind": "charge" }
                    }
                  ],
                  "totals": {
                    "productValue": { "amount": 99890, "currency": "PLN", "kind": "charge" },
                    "discountValue": { "amount": 10000, "currency": "PLN", "kind": "discount" },
                    "delivery": { "amount": 9990, "currency": "PLN", "kind": "charge" },
                    "totalWithDelivery": { "amount": 109880, "currency": "PLN", "kind": "charge" }
                  },
                  "deliveryEstimate": { "from": "2026-11-28", "to": "2026-11-30" }
                }
                """, basketId);
    }

    @Test
    void answersAnEmptyBasketWithAnExplicitNullEstimate() throws Exception {
        assertThat(HttpFixture.body(read())).contains("\"deliveryEstimate\":null");
        assertThat(HttpFixture.json(read(), "$.items")).asList().isEmpty();
    }

    @Test
    void pullsTheOfferOnEveryRenderBecauseTheAnswerMayHaveMoved() throws Exception {
        setQuantity(1);
        catalog.removed(FIRST_OFFER);

        assertThat(HttpFixture.json(read(), "$.items[0].availability.status")).isEqualTo("unavailable");
        assertThat(HttpFixture.json(read(), "$.items[0].availability.stale")).isEqualTo(false);
    }

    @Test
    void chargesNoDeliveryOnASmartTokenBecauseTheChannelTravelsInIt() throws Exception {
        setQuantity(1);

        MockHttpServletResponse response = HttpFixture.asCustomerOnChannel(mvc, customer, "smart",
                get("/baskets/{basketId}", basketId));

        assertThat(HttpFixture.json(response, "$.totals.delivery.amount")).isEqualTo(0);
        assertThat(HttpFixture.json(response, "$.shipments[0].delivery.amount")).isEqualTo(0);
    }

    @Test
    void refusesAnUnknownBasketAndNamesIt() throws Exception {
        MockHttpServletResponse response = HttpFixture.asCustomer(mvc, customer, get("/baskets/B-0000"));

        assertThat(response.getStatus()).isEqualTo(404);
        JsonAssert.assertThat(HttpFixture.body(response)).isExactlyLike("""
                { "error": "BASKET_NOT_FOUND", "basketId": "B-0000" }
                """);
    }

    @Test
    void refusesTheRenderWithoutAToken() throws Exception {
        MockHttpServletResponse response = HttpFixture.anonymous(mvc, get("/baskets/{basketId}", basketId));

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(HttpFixture.body(response)).isEmpty();
    }

    @Test
    void refusesTheRenderToTheServiceToken() throws Exception {
        MockHttpServletResponse response = HttpFixture.asService(mvc, get("/baskets/{basketId}", basketId));

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(HttpFixture.body(response)).isEmpty();
    }

    private MockHttpServletResponse read() throws Exception {
        return HttpFixture.asCustomer(mvc, customer, get("/baskets/{basketId}", basketId));
    }

    private void setQuantity(int quantity) throws Exception {
        HttpFixture.asCustomer(mvc, customer, patch("/baskets/{basketId}/items/{offerId}", basketId, FIRST_OFFER)
                .contentType(APPLICATION_JSON).content("{\"quantity\": %s}".formatted(quantity)));
    }
}
