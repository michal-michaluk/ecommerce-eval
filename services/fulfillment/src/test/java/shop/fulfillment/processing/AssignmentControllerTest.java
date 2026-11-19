package shop.fulfillment.processing;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import shop.fulfillment.HttpFixture;
import shop.fulfillment.IntegrationTest;
import shop.fulfillment.kernel.WarehouseStock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Element 07's route over the real chain: a granted claim is {@code 201} with the assignee the token named, a repeated claim
 * is {@code 200}, a foreign claim is {@code 409 ALREADY_ASSIGNED}, an unknown processing is {@code 404 PROCESSING_NOT_FOUND},
 * and no other scope may claim at all.
 *
 * <p>The warehouse is the in-test double the plan asks for (spec D14): no HTTP, one shelf, and every allocation recorded —
 * the claim's reservation is a fact the test can see rather than a service it has to trust.
 */
@IntegrationTest
class AssignmentControllerTest {

    @Autowired
    WebApplicationContext context;
    @Autowired
    OrderIntakeService orders;

    MockMvc mvc;

    @BeforeEach
    void buildsMockMvc() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void grantsTheClaimWith201AndTheCallerAsAssignee() throws Exception {
        String processingId = acceptedProcessing();

        MockHttpServletResponse response = claim(processingId, HttpFixture.EMPLOYEE_SCOPE, HttpFixture.EMPLOYEE);

        assertThat(response.getStatus()).isEqualTo(201);
        assertThat(HttpFixture.json(response, "$.state")).isEqualTo("assigned");
        assertThat(HttpFixture.json(response, "$.assignee")).isEqualTo(HttpFixture.EMPLOYEE);
    }

    @Test
    void refusesAForeignClaimWith409AndTheGuardsReason() throws Exception {
        String processingId = acceptedProcessing();
        claim(processingId, HttpFixture.EMPLOYEE_SCOPE, HttpFixture.EMPLOYEE);

        MockHttpServletResponse refused = claim(processingId, HttpFixture.EMPLOYEE_SCOPE, HttpFixture.OTHER_EMPLOYEE);

        assertThat(refused.getStatus()).isEqualTo(409);
        assertThat(HttpFixture.json(refused, "$.error")).isEqualTo("ALREADY_ASSIGNED");
    }

    @Test
    void answersARepeatedClaimWith200() throws Exception {
        String processingId = acceptedProcessing();
        claim(processingId, HttpFixture.EMPLOYEE_SCOPE, HttpFixture.EMPLOYEE);

        MockHttpServletResponse repeated = claim(processingId, HttpFixture.EMPLOYEE_SCOPE, HttpFixture.EMPLOYEE);

        assertThat(repeated.getStatus()).isEqualTo(200);
        assertThat(HttpFixture.json(repeated, "$.assignee")).isEqualTo(HttpFixture.EMPLOYEE);
    }

    @Test
    void answersAnUnknownProcessingWith404() throws Exception {
        MockHttpServletResponse response = claim("OP-2026-9999", HttpFixture.EMPLOYEE_SCOPE, HttpFixture.EMPLOYEE);

        assertThat(response.getStatus()).isEqualTo(404);
        assertThat(HttpFixture.json(response, "$.error")).isEqualTo("PROCESSING_NOT_FOUND");
    }

    @Test
    void refusesAClaimWithoutAToken() throws Exception {
        String processingId = acceptedProcessing();

        assertThat(HttpFixture.anonymous(mvc, post("/processing/" + processingId + "/assignment")
                .contentType(APPLICATION_JSON)).getStatus()).isEqualTo(401);
    }

    @Test
    void refusesAClaimFromACustomerScope() throws Exception {
        String processingId = acceptedProcessing();

        assertThat(claim(processingId, HttpFixture.CUSTOMER_SCOPE, HttpFixture.CUSTOMER).getStatus()).isEqualTo(403);
    }

    @Test
    void refusesAClaimFromTheResolverScope() throws Exception {
        String processingId = acceptedProcessing();

        assertThat(claim(processingId, HttpFixture.RESOLVER_SCOPE, "resolver").getStatus()).isEqualTo(403);
    }

    @Test
    void refusesAClaimFromTheShoppingScope() throws Exception {
        String processingId = acceptedProcessing();

        assertThat(claim(processingId, HttpFixture.SHOPPING_SCOPE, HttpFixture.SHOPPING).getStatus()).isEqualTo(403);
    }

    private MockHttpServletResponse claim(String processingId, String scope, String caller) throws Exception {
        return HttpFixture.as(scope, caller, mvc,
                post("/processing/" + processingId + "/assignment").contentType(APPLICATION_JSON));
    }

    private String acceptedProcessing() {
        return orders.accept(OrderHandoverFixture.aHandover()).processing().processingId().value();
    }

    @TestConfiguration
    static class WarehouseStub {

        @Bean
        WarehouseStock warehouseStock() {
            return new FakeWarehouseStock();
        }
    }
}
