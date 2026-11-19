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
 * Element 16's read surface over the real chain: every position on one screen with its line name and its stops, the
 * {@code n/m packed} counter, the assignee, and nothing the doc does not name — no money, no delivery data, no action of
 * another view's actor.
 */
@IntegrationTest
class PickWorkstationControllerTest {

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
    void servesThePositionsWithTheirNamesAndStops() throws Exception {
        String processingId = assignedProcessing();

        MockHttpServletResponse response = workstation(processingId);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(HttpFixture.json(response, "$.state")).isEqualTo("assigned");
        assertThat(HttpFixture.json(response, "$.assignee")).isEqualTo(HttpFixture.EMPLOYEE);
        assertThat(HttpFixture.json(response, "$.positions[0].productNumber")).isEqualTo("75423");
        assertThat(HttpFixture.json(response, "$.positions[0].name")).isEqualTo("LEGO X-Wing 75423");
        assertThat(HttpFixture.json(response, "$.positions[0].state")).isEqualTo("awaitingScan");
        assertThat(HttpFixture.json(response, "$.positions[0].locations[0].location"))
                .isEqualTo(FakeWarehouseStock.SHELF);
    }

    @Test
    void countsHowManyPositionsArePacked() throws Exception {
        String processingId = assignedProcessing();

        assertThat(HttpFixture.json(workstation(processingId), "$.positionProgress")).isEqualTo("0/1 packed");

        work.pick(processingId, "75423", HttpFixture.EMPLOYEE);
        work.pack(processingId, List.of("75423"), HttpFixture.EMPLOYEE);

        assertThat(HttpFixture.json(workstation(processingId), "$.positionProgress")).isEqualTo("1/1 packed");
    }

    @Test
    void keepsAConfirmedRowVisibleWithoutAStop() throws Exception {
        String processingId = assignedProcessing();
        work.pick(processingId, "75423", HttpFixture.EMPLOYEE);

        MockHttpServletResponse response = workstation(processingId);

        assertThat(HttpFixture.json(response, "$.positions[0].state")).isEqualTo("picked");
        assertThat(HttpFixture.json(response, "$.positions[0].locations")).isEqualTo(List.of());
    }

    @Test
    void servesNoMoneyAndNoDeliveryData() throws Exception {
        String processingId = assignedProcessing();

        String body = HttpFixture.body(workstation(processingId)).toLowerCase();

        assertThat(body).doesNotContain("price").doesNotContain("amount").doesNotContain("currency")
                .doesNotContain("total").doesNotContain("waybill").doesNotContain("legs");
    }

    @Test
    void refusesAResolverToken() throws Exception {
        String processingId = assignedProcessing();

        assertThat(HttpFixture.as(HttpFixture.RESOLVER_SCOPE, "resolver", mvc,
                get("/processing/" + processingId + "/pick-workstation")).getStatus()).isEqualTo(403);
    }

    @Test
    void answersAnUnknownProcessingWith404() throws Exception {
        MockHttpServletResponse response = HttpFixture.as(HttpFixture.EMPLOYEE_SCOPE, HttpFixture.EMPLOYEE, mvc,
                get("/processing/OP-2026-9999/pick-workstation"));

        assertThat(response.getStatus()).isEqualTo(404);
        assertThat(HttpFixture.json(response, "$.error")).isEqualTo("PROCESSING_NOT_FOUND");
    }

    private MockHttpServletResponse workstation(String processingId) throws Exception {
        return HttpFixture.as(HttpFixture.EMPLOYEE_SCOPE, HttpFixture.EMPLOYEE, mvc,
                get("/processing/" + processingId + "/pick-workstation"));
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
