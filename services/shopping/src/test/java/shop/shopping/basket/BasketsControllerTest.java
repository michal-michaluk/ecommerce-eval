package shop.shopping.basket;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import shop.shopping.FixedClockFixture;
import shop.shopping.HttpFixture;
import shop.shopping.IntegrationTest;
import shop.shopping.JsonAssert;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * {@code GET /baskets} over the real chain: the customer who has no basket is answered the one they always own, a later read
 * answers the same row, and nobody without the customer's scope reaches it (steps 1, 23 and 24 of the contract).
 */
@IntegrationTest
@Import(FixedClockFixture.class)
class BasketsControllerTest {

    @Autowired
    WebApplicationContext context;

    MockMvc mvc;

    @BeforeEach
    void buildsMockMvc() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void createsTheBasketTheCustomerHasNoneOfAndAnswersIt() throws Exception {
        MockHttpServletResponse response = list(HttpFixture.aCustomer());

        assertThat(response.getStatus()).isEqualTo(200);
        JsonAssert.assertThat(HttpFixture.body(response)).isExactlyLike("""
                { "baskets": [ { "basketId": "%s", "state": "active", "currency": "PLN" } ] }
                """, basketIdOf(response));
        assertThat(basketIdOf(response)).startsWith("B-");
    }

    @Test
    void answersTheSameBasketToALaterReadOfTheSameCustomer() throws Exception {
        String customer = HttpFixture.aCustomer();

        assertThat(basketIdOf(list(customer))).isEqualTo(basketIdOf(list(customer)));
        assertThat((Iterable<?>) HttpFixture.json(list(customer), "$.baskets")).hasSize(1);
    }

    @Test
    void refusesTheListWithoutAToken() throws Exception {
        MockHttpServletResponse response = HttpFixture.anonymous(mvc, get("/baskets"));

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getHeader("WWW-Authenticate")).isEqualTo("Bearer");
        assertThat(HttpFixture.body(response)).isEmpty();
    }

    @Test
    void refusesTheListToTheServiceTokenThatCarriesNoCustomerScope() throws Exception {
        MockHttpServletResponse response = HttpFixture.asService(mvc, get("/baskets"));

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(HttpFixture.body(response)).isEmpty();
    }

    private MockHttpServletResponse list(String customer) throws Exception {
        return HttpFixture.asCustomer(mvc, customer, get("/baskets"));
    }

    private static String basketIdOf(MockHttpServletResponse response) throws Exception {
        return (String) HttpFixture.json(response, "$.baskets[0].basketId");
    }
}
