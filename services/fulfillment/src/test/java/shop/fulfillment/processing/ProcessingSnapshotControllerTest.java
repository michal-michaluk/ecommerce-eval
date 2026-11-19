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
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * Element 11's processing resource over the real chain: one payload for the state read, the two explicit nulls of the
 * contract, the state word of a position, and the two scopes allowed to open it.
 */
@IntegrationTest
class ProcessingSnapshotControllerTest {

    @Autowired
    WebApplicationContext context;
    @Autowired
    OrderIntakeService orders;
    @Autowired
    AssignmentService assignments;

    MockMvc mvc;

    @BeforeEach
    void buildsMockMvc() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void servesTheStateOfAFreshProcessing() throws Exception {
        String processingId = acceptedProcessing();

        MockHttpServletResponse response = snapshot(processingId, HttpFixture.EMPLOYEE_SCOPE, HttpFixture.EMPLOYEE);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(HttpFixture.json(response, "$.processingId")).isEqualTo(processingId);
        assertThat(HttpFixture.json(response, "$.state")).isEqualTo("pending");
        assertThat(HttpFixture.json(response, "$.positions[0].productNumber")).isEqualTo("75423");
        assertThat(HttpFixture.json(response, "$.positions[0].quantity")).isEqualTo(1);
        assertThat(HttpFixture.json(response, "$.positions[0].state")).isEqualTo("awaitingScan");
        assertThat(HttpFixture.json(response, "$.openIssues")).isEqualTo(0);
    }

    @Test
    void servesAnUnclaimedOrderWithAnExplicitNullAssignee() throws Exception {
        String processingId = acceptedProcessing();

        MockHttpServletResponse response = snapshot(processingId, HttpFixture.EMPLOYEE_SCOPE, HttpFixture.EMPLOYEE);

        assertThat(HttpFixture.body(response)).contains("\"assignee\":null");
        assertThat(HttpFixture.body(response)).contains("\"parcel\":null");
        assertThat(HttpFixture.body(response)).contains("\"issue\":null");
    }

    @Test
    void neverServesTheOrderHash() throws Exception {
        String processingId = acceptedProcessing();

        MockHttpServletResponse response = snapshot(processingId, HttpFixture.EMPLOYEE_SCOPE, HttpFixture.EMPLOYEE);

        assertThat(HttpFixture.body(response)).doesNotContain("orderHash");
    }

    @Test
    void servesTheStateACommandLeftBehind() throws Exception {
        String processingId = acceptedProcessing();
        assignments.claim(processingId, HttpFixture.EMPLOYEE);

        MockHttpServletResponse response = snapshot(processingId, HttpFixture.EMPLOYEE_SCOPE, HttpFixture.EMPLOYEE);

        assertThat(HttpFixture.json(response, "$.state")).isEqualTo("assigned");
        assertThat(HttpFixture.json(response, "$.assignee")).isEqualTo(HttpFixture.EMPLOYEE);
    }

    @Test
    void opensTheStateForAResolver() throws Exception {
        String processingId = acceptedProcessing();

        assertThat(snapshot(processingId, HttpFixture.RESOLVER_SCOPE, "resolver").getStatus()).isEqualTo(200);
    }

    @Test
    void keepsTheCustomerScopeOut() throws Exception {
        String processingId = acceptedProcessing();

        assertThat(snapshot(processingId, HttpFixture.CUSTOMER_SCOPE, HttpFixture.CUSTOMER).getStatus()).isEqualTo(403);
    }

    @Test
    void answersAnUnknownProcessingWith404() throws Exception {
        MockHttpServletResponse response = snapshot("OP-2026-9999", HttpFixture.EMPLOYEE_SCOPE, HttpFixture.EMPLOYEE);

        assertThat(response.getStatus()).isEqualTo(404);
        assertThat(HttpFixture.json(response, "$.error")).isEqualTo("PROCESSING_NOT_FOUND");
    }

    @Test
    void refusesARequestWithoutAToken() throws Exception {
        String processingId = acceptedProcessing();

        assertThat(HttpFixture.anonymous(mvc, get("/processing/" + processingId)).getStatus()).isEqualTo(401);
    }

    private MockHttpServletResponse snapshot(String processingId, String scope, String caller) throws Exception {
        return HttpFixture.as(scope, caller, mvc, get("/processing/" + processingId));
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
