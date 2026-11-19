package shop.fulfillment.processing;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import shop.fulfillment.HttpFixture;
import shop.fulfillment.IntegrationTest;
import shop.fulfillment.kernel.OrderFixture;
import shop.fulfillment.kernel.OrderProcessingId;
import shop.fulfillment.kernel.ProcessingFixture;
import shop.fulfillment.kernel.WarehouseStock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Elements 02 and 16 over the real chain: one scan closes one position and starts the picking, a repeated scan is the
 * no-op of D-11-2, packing is the separate second step, and a position that was not picked is refused.
 */
@IntegrationTest
class ProcessingCommandControllerTest {

    @Autowired
    WebApplicationContext context;
    @Autowired
    OrderIntakeService orders;
    @Autowired
    AssignmentService assignments;
    @Autowired
    DocumentOrderProcessingRepository processings;

    MockMvc mvc;

    @BeforeEach
    void buildsMockMvc() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void picksTheScannedPositionAndStartsThePicking() throws Exception {
        String processingId = assignedProcessing();

        MockHttpServletResponse response = scan(processingId, "75423");

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(HttpFixture.json(response, "$.positions[0].state")).isEqualTo("picked");
        assertThat(HttpFixture.json(response, "$.state")).isEqualTo("picking");
    }

    @Test
    void answersARepeatedScanWithTheSameState() throws Exception {
        String processingId = assignedProcessing();
        scan(processingId, "75423");

        MockHttpServletResponse repeated = scan(processingId, "75423");

        assertThat(repeated.getStatus()).isEqualTo(200);
        assertThat(HttpFixture.json(repeated, "$.positions[0].state")).isEqualTo("picked");
    }

    @Test
    void packsThePickedPositionAndPacksTheOrder() throws Exception {
        String processingId = assignedProcessing();
        scan(processingId, "75423");

        MockHttpServletResponse packed = packing(processingId, "[\"75423\"]");

        assertThat(packed.getStatus()).isEqualTo(200);
        assertThat(HttpFixture.json(packed, "$.positions[0].state")).isEqualTo("packed");
        assertThat(HttpFixture.json(packed, "$.state")).isEqualTo("packed");
    }

    @Test
    void answersARepeatedPackWithTheSameState() throws Exception {
        String processingId = assignedProcessing();
        scan(processingId, "75423");
        packing(processingId, "[\"75423\"]");

        MockHttpServletResponse repeated = packing(processingId, "[\"75423\"]");

        assertThat(repeated.getStatus()).isEqualTo(200);
        assertThat(HttpFixture.json(repeated, "$.positions[0].state")).isEqualTo("packed");
    }

    @Test
    void refusesAnEmployeeWhoDoesNotHoldTheOrder() throws Exception {
        String processingId = assignedProcessing();

        MockHttpServletResponse refused = HttpFixture.as(HttpFixture.EMPLOYEE_SCOPE, HttpFixture.OTHER_EMPLOYEE, mvc,
                scanRequest(processingId));

        assertThat(refused.getStatus()).isEqualTo(409);
        assertThat(HttpFixture.json(refused, "$.error")).isEqualTo("ACTOR_NOT_ALLOWED");
    }

    @Test
    void refusesBothCommandsWhileTheOrderIsOnHold() throws Exception {
        String processingId = heldProcessing();

        MockHttpServletResponse scanned = scan(processingId, "75423");

        assertThat(scanned.getStatus()).isEqualTo(409);
        assertThat(HttpFixture.json(scanned, "$.error")).isEqualTo("ON_HOLD");
        assertThat(packing(processingId, "[\"75423\"]").getStatus()).isEqualTo(409);
    }

    @Test
    void refusesACommandOnAnOrderNobodyClaims() throws Exception {
        String processingId = orders.accept(OrderHandoverFixture.aHandover()).processing().processingId().value();

        MockHttpServletResponse refused = scan(processingId, "75423");

        assertThat(refused.getStatus()).isEqualTo(409);
        assertThat(HttpFixture.json(refused, "$.error")).isEqualTo("NOT_STARTED");
    }

    @Test
    void refusesAPositionThatWasNotPicked() throws Exception {
        String processingId = assignedProcessing();

        MockHttpServletResponse refused = packing(processingId, "[\"75423\"]");

        assertThat(refused.getStatus()).isEqualTo(409);
        assertThat(HttpFixture.json(refused, "$.error")).isEqualTo("POSITION_NOT_PICKED");
    }

    @Test
    void answersAnUnknownPositionWith404() throws Exception {
        String processingId = assignedProcessing();

        MockHttpServletResponse response = scan(processingId, "40404");

        assertThat(response.getStatus()).isEqualTo(404);
        assertThat(HttpFixture.json(response, "$.error")).isEqualTo("POSITION_NOT_FOUND");
    }

    @Test
    void answersAnUnknownProcessingWith404() throws Exception {
        MockHttpServletResponse response = scan("OP-2026-9999", "75423");

        assertThat(response.getStatus()).isEqualTo(404);
        assertThat(HttpFixture.json(response, "$.error")).isEqualTo("PROCESSING_NOT_FOUND");
    }

    @Test
    void refusesAConfirmationThatIsNotAPick() throws Exception {
        String processingId = assignedProcessing();

        assertThat(scanConfirming(processingId, "{\"confirmation\": \"packed\"}").getStatus()).isEqualTo(400);
    }

    @Test
    void refusesAnEmptyPackingRequest() throws Exception {
        String processingId = assignedProcessing();

        assertThat(packing(processingId, "[]").getStatus()).isEqualTo(400);
    }

    @Test
    void refusesAResolverTokenOnBothCommands() throws Exception {
        String processingId = assignedProcessing();

        assertThat(HttpFixture.as(HttpFixture.RESOLVER_SCOPE, "resolver", mvc, scanRequest(processingId)).getStatus())
                .isEqualTo(403);
        assertThat(HttpFixture.as(HttpFixture.RESOLVER_SCOPE, "resolver", mvc,
                packingRequest(processingId, "[\"75423\"]")).getStatus()).isEqualTo(403);
    }

    @Test
    void refusesACustomerTokenOnBothCommands() throws Exception {
        String processingId = assignedProcessing();

        assertThat(HttpFixture.as(HttpFixture.CUSTOMER_SCOPE, HttpFixture.CUSTOMER, mvc, scanRequest(processingId))
                .getStatus()).isEqualTo(403);
        assertThat(HttpFixture.as(HttpFixture.CUSTOMER_SCOPE, HttpFixture.CUSTOMER, mvc,
                packingRequest(processingId, "[\"75423\"]")).getStatus()).isEqualTo(403);
    }

    @Test
    void refusesARequestWithoutAToken() throws Exception {
        String processingId = assignedProcessing();

        assertThat(HttpFixture.anonymous(mvc, scanRequest(processingId)).getStatus()).isEqualTo(401);
    }

    private MockHttpServletResponse scan(String processingId, String productNumber) throws Exception {
        return scanConfirming(processingId, "{\"confirmation\": \"picked\"}", productNumber);
    }

    private MockHttpServletResponse scanConfirming(String processingId, String body) throws Exception {
        return scanConfirming(processingId, body, "75423");
    }

    private MockHttpServletResponse scanConfirming(String processingId, String body, String productNumber)
            throws Exception {
        return HttpFixture.as(HttpFixture.EMPLOYEE_SCOPE, HttpFixture.EMPLOYEE, mvc,
                post("/processing/" + processingId + "/positions/" + productNumber)
                        .contentType(APPLICATION_JSON).content(body));
    }

    private MockHttpServletResponse packing(String processingId, String productNumbers) throws Exception {
        return HttpFixture.as(HttpFixture.EMPLOYEE_SCOPE, HttpFixture.EMPLOYEE, mvc,
                packingRequest(processingId, productNumbers));
    }

    private MockHttpServletRequestBuilder scanRequest(String processingId) {
        return post("/processing/" + processingId + "/positions/75423")
                .contentType(APPLICATION_JSON)
                .content("{\"confirmation\": \"picked\"}");
    }

    private MockHttpServletRequestBuilder packingRequest(String processingId, String productNumbers) {
        return post("/processing/" + processingId + "/packing")
                .contentType(APPLICATION_JSON)
                .content("{\"productNumbers\": " + productNumbers + "}");
    }

    /** An order held by an issue: reached through the aggregate's own commands and stored, because no route reports one yet. */
    private String heldProcessing() {
        OrderProcessingId processingId = new OrderProcessingId("OP-2026-7777");
        OrderFulfillmentProcess held = AggregateFixture.pendingProcessingAs(processingId,
                OrderFixture.anOrder("sha256:held"));
        held.assignTo(HttpFixture.EMPLOYEE);
        held.pick("75423", HttpFixture.EMPLOYEE);
        held.reportIssue(ProcessingFixture.issueOn(processingId, ProcessingFixture.ISSUE_ID));
        processings.save(held);
        return processingId.value();
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
