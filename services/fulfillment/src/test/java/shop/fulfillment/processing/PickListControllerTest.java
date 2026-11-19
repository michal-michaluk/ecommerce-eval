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

import java.util.List;

import shop.fulfillment.HttpFixture;
import shop.fulfillment.IntegrationTest;
import shop.fulfillment.kernel.WarehouseStock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * Element 10's route over the real chain: the walk of an assigned order with its locations, the state word of a position,
 * and the scopes the matrix keeps out.
 */
@IntegrationTest
class PickListControllerTest {

    @Autowired
    WebApplicationContext context;
    @Autowired
    OrderIntakeService orders;
    @Autowired
    AssignmentService assignments;
    @Autowired
    ProcessingService work;

    MockMvc mvc;

    @BeforeEach
    void buildsMockMvc() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void servesTheWalkOfAnAssignedOrder() throws Exception {
        String processingId = assignedProcessing();

        MockHttpServletResponse response = pickList(processingId, HttpFixture.EMPLOYEE_SCOPE, HttpFixture.EMPLOYEE);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(HttpFixture.json(response, "$.lines[0].productNumber")).isEqualTo("75423");
        assertThat(HttpFixture.json(response, "$.lines[0].quantity")).isEqualTo(1);
        assertThat(HttpFixture.json(response, "$.lines[0].state")).isEqualTo("awaitingScan");
        assertThat(HttpFixture.json(response, "$.lines[0].locations[0].location"))
                .isEqualTo(FakeWarehouseStock.SHELF);
        assertThat(HttpFixture.json(response, "$.shortfalls")).isEqualTo(List.of());
    }

    @Test
    void walksAProductFromTheShelfTheWarehouseAnswered() throws Exception {
        String processingId = assignedProcessing();

        MockHttpServletResponse response = pickList(processingId, HttpFixture.EMPLOYEE_SCOPE, HttpFixture.EMPLOYEE);

        assertThat(HttpFixture.json(response, "$.lines[0].locations[0].quantityToTake")).isEqualTo(1);
        assertThat(HttpFixture.json(response, "$.lines[0].locations[0].available")).isEqualTo(10);
    }

    @Test
    void walksNothingOnceThePositionWasScanned() throws Exception {
        String processingId = assignedProcessing();
        work.pick(processingId, "75423", HttpFixture.EMPLOYEE);

        MockHttpServletResponse response = pickList(processingId, HttpFixture.EMPLOYEE_SCOPE, HttpFixture.EMPLOYEE);

        assertThat(HttpFixture.json(response, "$.lines")).isEqualTo(List.of());
    }

    @Test
    void answersAnUnknownProcessingWith404() throws Exception {
        MockHttpServletResponse response = pickList("OP-2026-9999", HttpFixture.EMPLOYEE_SCOPE, HttpFixture.EMPLOYEE);

        assertThat(response.getStatus()).isEqualTo(404);
        assertThat(HttpFixture.json(response, "$.error")).isEqualTo("PROCESSING_NOT_FOUND");
    }

    @Test
    void refusesAResolverToken() throws Exception {
        String processingId = assignedProcessing();

        assertThat(pickList(processingId, HttpFixture.RESOLVER_SCOPE, "resolver").getStatus()).isEqualTo(403);
    }

    @Test
    void refusesACustomerToken() throws Exception {
        String processingId = assignedProcessing();

        assertThat(pickList(processingId, HttpFixture.CUSTOMER_SCOPE, HttpFixture.CUSTOMER).getStatus()).isEqualTo(403);
    }

    @Test
    void refusesARequestWithoutAToken() throws Exception {
        String processingId = assignedProcessing();

        assertThat(HttpFixture.anonymous(mvc, get("/processing/" + processingId + "/pick-list")).getStatus())
                .isEqualTo(401);
    }

    private MockHttpServletResponse pickList(String processingId, String scope, String caller) throws Exception {
        return HttpFixture.as(scope, caller, mvc, get("/processing/" + processingId + "/pick-list"));
    }

    private String assignedProcessing() {
        String processingId = orders.accept(OrderHandoverFixture.aHandover()).processing().processingId().value();
        assignments.claim(processingId, HttpFixture.EMPLOYEE);
        return processingId;
    }

    @TestConfiguration
    static class WarehouseStub {

        @Bean
        WarehouseStock warehouseStock() {
            return new FakeWarehouseStock();
        }
    }
}
