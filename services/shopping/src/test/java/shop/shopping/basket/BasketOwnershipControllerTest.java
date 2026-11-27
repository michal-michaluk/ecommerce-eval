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
 * Element 07 §Ownership (D68) over the real chain: {@code SCOPE_customer} says <em>a</em> customer, never <em>this</em> customer,
 * so every route compares the basket's {@code customerId} with the token's subject — and a basket owned by somebody else answers
 * exactly what an unknown id answers, {@code 404 BASKET_NOT_FOUND}, never a {@code 403} that would confirm the id exists and never
 * an answer that echoes the owner.
 *
 * <p>All five routes that carry an id are here because the check lives in the slice's load path and not in a controller: if one
 * route skipped it, this class would be the only thing that noticed.
 */
@IntegrationTest
@Import(FixedClockFixture.class)
@TestPropertySource(properties = {"basket.delivery.rate=9990", "basket.delivery.free-when-smart=true"})
class BasketOwnershipControllerTest {

    @Autowired
    WebApplicationContext context;
    @Autowired
    OfferCatalog offers;

    MockMvc mvc;
    String owner;
    String intruder;
    String basketId;

    @BeforeEach
    void buildsTheBasketOfAnotherCustomer() throws Exception {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        FixtureOfferCatalog catalog = (FixtureOfferCatalog) offers;
        catalog.failingWith(FixtureOfferCatalog.Failure.none);
        catalog.sells(FIRST_OFFER, 5);
        owner = HttpFixture.aCustomer();
        intruder = HttpFixture.aCustomer();
        basketId = (String) HttpFixture.json(
                HttpFixture.asCustomer(mvc, owner, get("/baskets")), "$.baskets[0].basketId");
        HttpFixture.asCustomer(mvc, owner, patch("/baskets/{basketId}/items/{offerId}", basketId, FIRST_OFFER)
                .contentType(APPLICATION_JSON).content("{\"quantity\": 1}"));
    }

    @Test
    void answersAnIntrudersBasketAsUnknownOnEveryRouteThatCarriesAnId() throws Exception {
        assertUnknown(read());
        assertUnknown(setQuantity());
        assertUnknown(removeEverything());
        assertUnknown(deselectEverything());
        assertUnknown(handOver());
    }

    @Test
    void keepsTheOwnersBasketWorkingWhileTheIntruderIsRefused() throws Exception {
        assertUnknown(read());

        MockHttpServletResponse owned = HttpFixture.asCustomer(mvc, owner, get("/baskets/{basketId}", basketId));

        assertThat(owned.getStatus()).isEqualTo(200);
        assertThat(HttpFixture.json(owned, "$.items[0].offerId")).isEqualTo(FIRST_OFFER);
        assertThat(HttpFixture.json(owned, "$.items[0].quantity")).isEqualTo(1);
    }

    @Test
    void refusesTheHandoverOfAForeignBasketWithoutNamingItsOwner() throws Exception {
        MockHttpServletResponse response = handOver();

        assertThat(response.getStatus()).isEqualTo(404);
        assertThat(HttpFixture.body(response)).doesNotContain(owner);
        assertThat(HttpFixture.body(response)).doesNotContain("customerId", "selection", "handoverId");
    }

    @Test
    void leavesTheOwnersBasketUnchangedByEveryRefusedCommand() throws Exception {
        setQuantity();
        removeEverything();
        deselectEverything();

        MockHttpServletResponse owned = HttpFixture.asCustomer(mvc, owner, get("/baskets/{basketId}", basketId));

        assertThat(HttpFixture.json(owned, "$.items[0].offerId")).isEqualTo(FIRST_OFFER);
        assertThat(HttpFixture.json(owned, "$.items[0].selected")).isEqualTo(true);
    }

    @Test
    void neverListsAnotherCustomersBasket() throws Exception {
        MockHttpServletResponse listed = HttpFixture.asCustomer(mvc, intruder, get("/baskets"));

        assertThat(HttpFixture.json(listed, "$.baskets.length()")).isEqualTo(1);
        assertThat((String) HttpFixture.json(listed, "$.baskets[0].basketId")).isNotEqualTo(basketId);
    }

    private void assertUnknown(MockHttpServletResponse response) throws Exception {
        assertThat(response.getStatus()).isEqualTo(404);
        JsonAssert.assertThat(HttpFixture.body(response)).isExactlyLike("""
                { "error": "BASKET_NOT_FOUND", "basketId": "%s" }
                """, basketId);
    }

    private MockHttpServletResponse read() throws Exception {
        return HttpFixture.asCustomer(mvc, intruder, get("/baskets/{basketId}", basketId));
    }

    private MockHttpServletResponse setQuantity() throws Exception {
        return HttpFixture.asCustomer(mvc, intruder, patch("/baskets/{basketId}/items/{offerId}", basketId, FIRST_OFFER)
                .contentType(APPLICATION_JSON).content("{\"quantity\": 3}"));
    }

    private MockHttpServletResponse removeEverything() throws Exception {
        return HttpFixture.asCustomer(mvc, intruder, patch("/baskets/{basketId}/items", basketId)
                .contentType(APPLICATION_JSON).content("{\"scope\": \"all\"}"));
    }

    private MockHttpServletResponse deselectEverything() throws Exception {
        return HttpFixture.asCustomer(mvc, intruder, patch("/baskets/{basketId}/selection", basketId)
                .contentType(APPLICATION_JSON).content("{\"scope\": \"all\", \"selected\": false}"));
    }

    private MockHttpServletResponse handOver() throws Exception {
        return HttpFixture.asCustomer(mvc, intruder, patch("/baskets/{basketId}/checkout", basketId));
    }
}
