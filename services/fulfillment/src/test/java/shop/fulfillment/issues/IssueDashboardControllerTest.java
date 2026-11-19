package shop.fulfillment.issues;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import shop.fulfillment.HandoverJson;
import shop.fulfillment.FakePorts;
import shop.fulfillment.HttpFixture;
import shop.fulfillment.IntegrationTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Element 17 over the real chain: the resolver reads the open gaps, an employee token is refused, and there is no write path
 * at all — the dashboard answers {@code GET} and nothing else, because closing an issue is the resume command's fact
 * (register D8).
 */
@IntegrationTest
@Import(FakePorts.class)
class IssueDashboardControllerTest {

    @Autowired
    WebApplicationContext context;

    MockMvc mvc;

    @BeforeEach
    void buildsMockMvc() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void showsTheOpenIssuesToTheResolver() throws Exception {
        String processingId = heldProcessing();

        MockHttpServletResponse response = list(HttpFixture.RESOLVER_SCOPE, HttpFixture.RESOLVER);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(HttpFixture.json(response, "$.items[0].issueId")).asString().startsWith("ISS-");
        assertThat(HttpFixture.json(response, "$.items[0].kind")).isEqualTo("LOST");
        assertThat(HttpFixture.json(response, "$.items[0].processingId")).isEqualTo(processingId);
        assertThat(HttpFixture.json(response, "$.items[0].orderState")).isEqualTo("onHold");
        assertThat(HttpFixture.json(response, "$.items[0].assignee")).isEqualTo(HttpFixture.EMPLOYEE);
        assertThat(HttpFixture.json(response, "$.items[0].product.productNumber")).isEqualTo("75423");
        assertThat(HttpFixture.json(response, "$.items[0].product.name")).isEqualTo("LEGO X-Wing 75423");
        assertThat(HttpFixture.json(response, "$.items[0].reportedBy")).isEqualTo(HttpFixture.EMPLOYEE);
    }

    @Test
    void refusesTheDashboardToTheEmployeeAndTheCustomer() throws Exception {
        heldProcessing();

        assertThat(list(HttpFixture.EMPLOYEE_SCOPE, HttpFixture.EMPLOYEE).getStatus()).isEqualTo(403);
        assertThat(list(HttpFixture.CUSTOMER_SCOPE, HttpFixture.CUSTOMER).getStatus()).isEqualTo(403);
        assertThat(HttpFixture.anonymous(mvc, get("/issues")).getStatus()).isEqualTo(401);
    }

    @Test
    void hasNoWritePathAtAll() throws Exception {
        String processingId = heldProcessing();

        MockHttpServletResponse closed = HttpFixture.as(HttpFixture.RESOLVER_SCOPE, HttpFixture.RESOLVER, mvc,
                post("/issues").contentType(org.springframework.http.MediaType.APPLICATION_JSON).content("{}"));

        assertThat(closed.getStatus()).isEqualTo(403);
        assertThat(list(HttpFixture.RESOLVER_SCOPE, HttpFixture.RESOLVER).getStatus()).isEqualTo(200);
        assertThat(HttpFixture.json(list(HttpFixture.RESOLVER_SCOPE, HttpFixture.RESOLVER), "$.items[0].processingId"))
                .isEqualTo(processingId);
    }

    @Test
    void carriesNoQuantityNoLocationAndNoMoney() throws Exception {
        heldProcessing();

        assertThat(HttpFixture.body(list(HttpFixture.RESOLVER_SCOPE, HttpFixture.RESOLVER)))
                .doesNotContain("quantity", "location", "price", "amount", "currency");
    }

    @Test
    void answersAnEmptyListWhenNothingIsHeld() throws Exception {
        assertThat(HttpFixture.json(list(HttpFixture.RESOLVER_SCOPE, HttpFixture.RESOLVER), "$.items")).asList()
                .isEmpty();
    }

    private MockHttpServletResponse list(String scope, String caller) throws Exception {
        return HttpFixture.as(scope, caller, mvc, get("/issues"));
    }

    /** The whole lane over its own contract: hand over, claim, scan one position, report the gap it tripped over. */
    private String heldProcessing() throws Exception {
        MockHttpServletResponse handed = HttpFixture.as(HttpFixture.SHOPPING_SCOPE, HttpFixture.SHOPPING, mvc,
                post("/orders").contentType(APPLICATION_JSON).content(HandoverJson.complete()));
        String processingId = (String) HttpFixture.json(handed, "$.processingId");
        HttpFixture.as(HttpFixture.EMPLOYEE_SCOPE, HttpFixture.EMPLOYEE, mvc,
                post("/processing/" + processingId + "/assignment"));
        HttpFixture.as(HttpFixture.EMPLOYEE_SCOPE, HttpFixture.EMPLOYEE, mvc,
                post("/processing/" + processingId + "/positions/75423").contentType(APPLICATION_JSON)
                        .content("{\"confirmation\": \"picked\"}"));
        HttpFixture.as(HttpFixture.EMPLOYEE_SCOPE, HttpFixture.EMPLOYEE, mvc,
                post("/processing/" + processingId + "/issues").contentType(APPLICATION_JSON)
                        .content("{\"kind\": \"LOST\", \"productNumber\": \"75423\"}"));
        return processingId;
    }

}
