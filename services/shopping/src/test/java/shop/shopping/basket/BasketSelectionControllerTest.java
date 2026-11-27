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
 * {@code PATCH /baskets/{basketId}/selection} over the real chain: the three checkboxes of element 01, and the rule that the
 * totals follow the selection rather than the basket (VIEW-1, D18, steps 12, 13, 15 and 17 of the contract).
 *
 * <p>Selection is state and not history (RULE-10), so no event is recorded and nothing about it can go stale in a projection —
 * which is why the render of one basket reads the write model.
 */
@IntegrationTest
@Import(FixedClockFixture.class)
@TestPropertySource(properties = {"basket.delivery.rate=9990", "basket.delivery.free-when-smart=true"})
class BasketSelectionControllerTest {

    @Autowired
    WebApplicationContext context;
    @Autowired
    OfferCatalog offers;

    MockMvc mvc;
    String customer;
    String basketId;

    @BeforeEach
    void buildsABasketOfTwoSelectedOffers() throws Exception {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        FixtureOfferCatalog catalog = (FixtureOfferCatalog) offers;
        catalog.failingWith(FixtureOfferCatalog.Failure.none);
        catalog.sells(FIRST_OFFER, 5);
        catalog.sells(SECOND_OFFER, 5);
        customer = HttpFixture.aCustomer();
        basketId = (String) HttpFixture.json(
                HttpFixture.asCustomer(mvc, customer, get("/baskets")), "$.baskets[0].basketId");
        addToBasket(FIRST_OFFER);
        addToBasket(SECOND_OFFER);
    }

    @Test
    void deselectingOneRowTakesItOutOfTheShipmentAndOutOfTheTotals() throws Exception {
        MockHttpServletResponse response = select("""
                { "scope": "item", "offerId": "%s", "selected": false }
                """.formatted(SECOND_OFFER));

        assertThat(response.getStatus()).isEqualTo(200);
        JsonAssert.assertThat(HttpFixture.body(response)).hasFieldsLike("""
                {
                  "items": [
                    { "offerId": "OF-1001", "selected": true },
                    { "offerId": "OF-1002", "selected": false }
                  ],
                  "shipments": [ { "seller": "FIXTURE_SELLER", "items": ["OF-1001"] } ],
                  "totals": { "productValue": { "amount": 99890 }, "delivery": { "amount": 9990 },
                              "totalWithDelivery": { "amount": 109880 } }
                }
                """);
    }

    @Test
    void deselectingTheWholeBasketLeavesAValidEmptySelectionAndSaysThatNothingIsHandable() throws Exception {
        MockHttpServletResponse response = select("""
                { "scope": "all", "selected": false }
                """);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(HttpFixture.json(response, "$.items[0].selected")).isEqualTo(false);
        assertThat(HttpFixture.json(response, "$.items[1].selected")).isEqualTo(false);
        assertThat(HttpFixture.json(response, "$.shipments")).asList().isEmpty();
        assertThat(HttpFixture.json(response, "$.totals.totalWithDelivery.amount")).isEqualTo(0);
        assertThat(HttpFixture.json(response, "$.deliveryEstimate")).isNull();
    }

    @Test
    void selectingTheWholeBasketPutsEveryRowBackIntoTheTotals() throws Exception {
        select("""
                { "scope": "all", "selected": false }
                """);

        MockHttpServletResponse response = select("""
                { "scope": "all", "selected": true }
                """);

        assertThat(HttpFixture.json(response, "$.items[0].selected")).isEqualTo(true);
        assertThat(HttpFixture.json(response, "$.totals.productValue.amount")).isEqualTo(199780);
    }

    @Test
    void deselectsEveryRowOfOneShipmentWhenTheScopeNamesTheSeller() throws Exception {
        MockHttpServletResponse response = select("""
                { "scope": "shipment", "seller": "FIXTURE_SELLER", "selected": false }
                """);

        assertThat(HttpFixture.json(response, "$.items[0].selected")).isEqualTo(false);
        assertThat(HttpFixture.json(response, "$.shipments")).asList().isEmpty();
    }

    @Test
    void refusesAnOfferTheBasketDoesNotHoldAndNamesIt() throws Exception {
        MockHttpServletResponse response = select("""
                { "scope": "item", "offerId": "OF-9999", "selected": false }
                """);

        assertThat(response.getStatus()).isEqualTo(404);
        JsonAssert.assertThat(HttpFixture.body(response)).isExactlyLike("""
                { "error": "OFFER_NOT_FOUND", "offerId": "OF-9999" }
                """);
    }

    @Test
    void refusesACommandThatNamesNoSubjectOnItsOwn() throws Exception {
        assertThat(select("""
                { "scope": "item", "selected": false }
                """).getStatus()).isEqualTo(400);
    }

    @Test
    void refusesACommandWithoutAScopeAsTheMalformedRequestItIs() throws Exception {
        assertThat(select("""
                { "selected": true }
                """).getStatus()).isEqualTo(400);
    }

    @Test
    void refusesTheSelectionWithoutAToken() throws Exception {
        MockHttpServletResponse response = HttpFixture.anonymous(mvc, patch("/baskets/{basketId}/selection", basketId)
                .contentType(APPLICATION_JSON).content("{\"scope\": \"all\", \"selected\": true}"));

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(HttpFixture.body(response)).isEmpty();
    }

    @Test
    void refusesTheSelectionToTheServiceToken() throws Exception {
        MockHttpServletResponse response = HttpFixture.asService(mvc, patch("/baskets/{basketId}/selection", basketId)
                .contentType(APPLICATION_JSON).content("{\"scope\": \"all\", \"selected\": true}"));

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(HttpFixture.body(response)).isEmpty();
    }

    private void addToBasket(String offerId) throws Exception {
        HttpFixture.asCustomer(mvc, customer, patch("/baskets/{basketId}/items/{offerId}", basketId, offerId)
                .contentType(APPLICATION_JSON).content("{\"quantity\": 1}"));
    }

    private MockHttpServletResponse select(String body) throws Exception {
        return HttpFixture.asCustomer(mvc, customer, patch("/baskets/{basketId}/selection", basketId)
                .contentType(APPLICATION_JSON).content(body));
    }
}
