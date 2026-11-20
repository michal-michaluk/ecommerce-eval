package shop.fulfillment.orders;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import shop.fulfillment.HandoverJson;
import shop.fulfillment.HttpFixture;
import shop.fulfillment.IntegrationTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Element 15's route over the real chain: the employee's list, served from the read model the handover's own event fed, with
 * the row shape element 11 asserts and no money anywhere.
 */
@IntegrationTest
class OrdersListControllerTest {

    @Autowired
    WebApplicationContext context;

    MockMvc mvc;

    @BeforeEach
    void buildsMockMvc() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void answersEveryCurrentOrderWithItsStateAndRecipient() throws Exception {
        String processingId = acceptedProcessing();

        MockHttpServletResponse response = list();

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(HttpFixture.json(response, "$.items[0].processingId")).isEqualTo(processingId);
        assertThat(HttpFixture.json(response, "$.items[0].state")).isEqualTo("pending");
        assertThat(HttpFixture.json(response, "$.items[0].recipient")).isEqualTo("Jan Kowalski");
        assertThat(HttpFixture.json(response, "$.items[0].positionProgress")).isEqualTo("0/1 packed");
    }

    @Test
    void statesAnUnclaimedAssigneeAsAnExplicitNull() throws Exception {
        acceptedProcessing();

        MockHttpServletResponse response = list();

        assertThat(HttpFixture.body(response)).contains("\"assignee\":null");
        assertThat(HttpFixture.json(response, "$.items[0].assignee")).isNull();
    }

    @Test
    void answersAnEmptyListWhenNoOrderIsBeingProcessed() throws Exception {
        assertThat(HttpFixture.json(list(), "$.items")).asList().isEmpty();
    }

    @Test
    void refusesTheListWithoutAToken() throws Exception {
        assertThat(HttpFixture.anonymous(mvc, get("/orders")).getStatus()).isEqualTo(401);
    }

    @Test
    void refusesTheListToTheResolverScope() throws Exception {
        assertThat(HttpFixture.as(HttpFixture.RESOLVER_SCOPE, "resolver", mvc, get("/orders")).getStatus()).isEqualTo(403);
    }

    @Test
    void carriesNoMoneyInAnyRow() throws Exception {
        acceptedProcessing();

        assertThat(HttpFixture.body(list())).doesNotContain("price", "amount", "currency", "vat");
    }

    private MockHttpServletResponse list() throws Exception {
        return HttpFixture.as(HttpFixture.EMPLOYEE_SCOPE, HttpFixture.EMPLOYEE, mvc, get("/orders"));
    }

    private String acceptedProcessing() throws Exception {
        MockHttpServletResponse accepted = HttpFixture.as(HttpFixture.SHOPPING_SCOPE, HttpFixture.SHOPPING, mvc,
                post("/orders").contentType(APPLICATION_JSON).content(HandoverJson.complete()));
        return (String) HttpFixture.json(accepted, "$.processingId");
    }
}
