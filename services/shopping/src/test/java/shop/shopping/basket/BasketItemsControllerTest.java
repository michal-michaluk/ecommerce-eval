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
 * The two item routes over the real chain: the absolute quantity {@code PATCH …/items/{offerId}} sets (D28, F16 — a repeated
 * command is the quantity it states, never a change), and the three removal scopes {@code PATCH …/items} owns (element 07
 * §Removal, where {@code quantity: null} is what removes).
 */
@IntegrationTest
@Import(FixedClockFixture.class)
@TestPropertySource(properties = {"basket.delivery.rate=9990", "basket.delivery.free-when-smart=true"})
class BasketItemsControllerTest {

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
    void addsAnOfferAtTheQuantityThePathStatesAndSelectsIt() throws Exception {
        MockHttpServletResponse response = setQuantity(FIRST_OFFER, "2");

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(HttpFixture.json(response, "$.items[0].offerId")).isEqualTo(FIRST_OFFER);
        assertThat(HttpFixture.json(response, "$.items[0].quantity")).isEqualTo(2);
        assertThat(HttpFixture.json(response, "$.items[0].selected")).isEqualTo(true);
        assertThat(HttpFixture.json(response, "$.items[0].lineTotal.amount")).isEqualTo(199780);
    }

    @Test
    void repeatsTheAbsoluteQuantityInsteadOfDoublingTheRow() throws Exception {
        setQuantity(FIRST_OFFER, "1");

        assertThat(HttpFixture.json(setQuantity(FIRST_OFFER, "1"), "$.items[0].quantity")).isEqualTo(1);
    }

    @Test
    void refusesAQuantityThatIsNotAQuantity() throws Exception {
        MockHttpServletResponse response = setQuantity(FIRST_OFFER, "0");

        assertThat(response.getStatus()).isEqualTo(422);
        JsonAssert.assertThat(HttpFixture.body(response)).isExactlyLike("""
                { "error": "QUANTITY_NOT_POSITIVE" }
                """);
    }

    @Test
    void refusesAnOfferTheCatalogDoesNotSellAndNamesIt() throws Exception {
        MockHttpServletResponse response = setQuantity("OF-0000", "1");

        assertThat(response.getStatus()).isEqualTo(404);
        JsonAssert.assertThat(HttpFixture.body(response)).isExactlyLike("""
                { "error": "OFFER_NOT_FOUND", "offerId": "OF-0000" }
                """);
    }

    @Test
    void clearsTheBasketOnTheScopeThatCoversEverything() throws Exception {
        setQuantity(FIRST_OFFER, "1");
        setQuantity(SECOND_OFFER, "1");

        MockHttpServletResponse response = remove("""
                { "scope": "all", "quantity": null }
                """);

        assertThat(response.getStatus()).isEqualTo(200);
        JsonAssert.assertThat(HttpFixture.body(response)).isExactlyLike("""
                {
                  "basketId": "%s",
                  "state": "active",
                  "currency": "PLN",
                  "items": [],
                  "shipments": [],
                  "totals": {
                    "productValue": { "amount": 0, "currency": "PLN", "kind": "charge" },
                    "discountValue": { "amount": 0, "currency": "PLN", "kind": "discount" },
                    "delivery": { "amount": 0, "currency": "PLN", "kind": "charge" },
                    "totalWithDelivery": { "amount": 0, "currency": "PLN", "kind": "charge" }
                  },
                  "deliveryEstimate": null
                }
                """, basketId);
    }

    @Test
    void removesTheOneRowTheScopeNames() throws Exception {
        setQuantity(FIRST_OFFER, "1");
        setQuantity(SECOND_OFFER, "1");

        MockHttpServletResponse response = remove("""
                { "scope": "item", "offerId": "%s", "quantity": null }
                """.formatted(SECOND_OFFER));

        assertThat(HttpFixture.json(response, "$.items.length()")).isEqualTo(1);
        assertThat(HttpFixture.json(response, "$.items[0].offerId")).isEqualTo(FIRST_OFFER);
    }

    @Test
    void removesOnlyTheRowsTheSelectionCovers() throws Exception {
        setQuantity(FIRST_OFFER, "1");
        setQuantity(SECOND_OFFER, "1");
        HttpFixture.asCustomer(mvc, customer, patch("/baskets/{basketId}/selection", basketId)
                .contentType(APPLICATION_JSON).content("""
                        { "scope": "item", "offerId": "%s", "selected": false }
                        """.formatted(FIRST_OFFER)));

        MockHttpServletResponse response = remove("""
                { "scope": "selection", "quantity": null }
                """);

        assertThat(HttpFixture.json(response, "$.items.length()")).isEqualTo(1);
        assertThat(HttpFixture.json(response, "$.items[0].offerId")).isEqualTo(FIRST_OFFER);
    }

    @Test
    void refusesRemovingAnOfferTheBasketDoesNotHold() throws Exception {
        MockHttpServletResponse response = remove("""
                { "scope": "item", "offerId": "%s", "quantity": null }
                """.formatted(SECOND_OFFER));

        assertThat(response.getStatus()).isEqualTo(404);
        JsonAssert.assertThat(HttpFixture.body(response)).isExactlyLike("""
                { "error": "OFFER_NOT_FOUND", "offerId": "OF-1002" }
                """);
    }

    @Test
    void refusesAQuantityOnTheRouteThatOnlyRemoves() throws Exception {
        setQuantity(FIRST_OFFER, "4");

        MockHttpServletResponse response = remove("""
                { "scope": "item", "offerId": "%s", "quantity": 1 }
                """.formatted(FIRST_OFFER));

        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(HttpFixture.json(getBasket(), "$.items[0].quantity")).isEqualTo(4);
    }

    @Test
    void refusesBothRoutesWithoutAToken() throws Exception {
        assertThat(HttpFixture.anonymous(mvc, patch("/baskets/{basketId}/items/{offerId}", basketId, FIRST_OFFER)
                .contentType(APPLICATION_JSON).content("{\"quantity\": 1}")).getStatus()).isEqualTo(401);
        assertThat(HttpFixture.anonymous(mvc, patch("/baskets/{basketId}/items", basketId)
                .contentType(APPLICATION_JSON).content("{\"scope\": \"all\"}")).getStatus()).isEqualTo(401);
    }

    @Test
    void refusesBothRoutesToTheServiceToken() throws Exception {
        assertThat(HttpFixture.asService(mvc, patch("/baskets/{basketId}/items/{offerId}", basketId, FIRST_OFFER)
                .contentType(APPLICATION_JSON).content("{\"quantity\": 1}")).getStatus()).isEqualTo(403);
        assertThat(HttpFixture.asService(mvc, patch("/baskets/{basketId}/items", basketId)
                .contentType(APPLICATION_JSON).content("{\"scope\": \"all\"}")).getStatus()).isEqualTo(403);
    }

    private MockHttpServletResponse setQuantity(String offerId, String quantity) throws Exception {
        return HttpFixture.asCustomer(mvc, customer, patch("/baskets/{basketId}/items/{offerId}", basketId, offerId)
                .contentType(APPLICATION_JSON).content("{\"quantity\": %s}".formatted(quantity)));
    }

    private MockHttpServletResponse remove(String body) throws Exception {
        return HttpFixture.asCustomer(mvc, customer, patch("/baskets/{basketId}/items", basketId)
                .contentType(APPLICATION_JSON).content(body));
    }

    private MockHttpServletResponse getBasket() throws Exception {
        return HttpFixture.asCustomer(mvc, customer, get("/baskets/{basketId}", basketId));
    }
}
