package shop.fulfillment.customer;

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
import shop.fulfillment.kernel.CarrierPort;
import shop.fulfillment.kernel.WarehouseStock;
import shop.fulfillment.parcel.FakeCarrierPort;
import shop.fulfillment.processing.FakeWarehouseStock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Element 18 over the real chain: the customer scope reaches its own resource, the payload carries the state and the delivery
 * timeline, and neither {@code assignee} nor {@code issue} exists in it — the employee's snapshot is a different resource and
 * stays closed to this scope.
 */
@IntegrationTest
class CustomerStateControllerTest {

    @Autowired
    WebApplicationContext context;

    MockMvc mvc;

    @BeforeEach
    void buildsMockMvc() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void servesTheStateTheSummaryAndTheTimeline() throws Exception {
        String processingId = readyProcessing();

        MockHttpServletResponse response = state(processingId);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(HttpFixture.json(response, "$.state")).isEqualTo("readyForDelivery");
        assertThat(HttpFixture.json(response, "$.lines[0].productNumber")).isEqualTo("75423");
        assertThat(HttpFixture.json(response, "$.lines[0].quantity")).isEqualTo(1);
        assertThat(HttpFixture.json(response, "$.delivery.methodCode")).isEqualTo("PARCEL_LOCKER");
        assertThat(HttpFixture.json(response, "$.delivery.location.pointId")).isEqualTo("KRA01M");
        assertThat(HttpFixture.json(response, "$.legs[0].leg")).isEqualTo("L1");
        assertThat(HttpFixture.json(response, "$.legs[1].leg")).isEqualTo("L2");
    }

    @Test
    void neverServesAnAssigneeAnIssueOrAnyMoney() throws Exception {
        String processingId = readyProcessing();

        String body = HttpFixture.body(state(processingId));

        assertThat(body).doesNotContain("assignee").doesNotContain("issue").doesNotContain("openIssues")
                .doesNotContain("price").doesNotContain("amount").doesNotContain("currency")
                .doesNotContain("buyerName").doesNotContain("waybill");
    }

    @Test
    void keepsTheEmployeeSnapshotOutOfTheCustomerScope() throws Exception {
        String processingId = readyProcessing();

        assertThat(HttpFixture.as(HttpFixture.CUSTOMER_SCOPE, HttpFixture.CUSTOMER, mvc,
                get("/processing/" + processingId)).getStatus()).isEqualTo(403);
        assertThat(HttpFixture.as(HttpFixture.EMPLOYEE_SCOPE, HttpFixture.EMPLOYEE, mvc,
                get("/processing/" + processingId + "/state")).getStatus()).isEqualTo(403);
    }

    @Test
    void answersAnUnknownProcessingWithTheContractsRefusal() throws Exception {
        MockHttpServletResponse response = state("OP-2026-9999");

        assertThat(response.getStatus()).isEqualTo(404);
        assertThat(HttpFixture.json(response, "$.error")).isEqualTo("PROCESSING_NOT_FOUND");
    }

    private MockHttpServletResponse state(String processingId) throws Exception {
        return HttpFixture.as(HttpFixture.CUSTOMER_SCOPE, HttpFixture.CUSTOMER, mvc,
                get("/processing/" + processingId + "/state"));
    }

    /** One order walked to {@code readyForDelivery} through the wire, so the projection is fed by real events. */
    private String readyProcessing() throws Exception {
        String processingId = String.valueOf(HttpFixture.json(
                as(HttpFixture.SHOPPING_SCOPE, HttpFixture.SHOPPING, post("/orders").contentType(APPLICATION_JSON)
                        .content("""
                                {"orderHash": "sha256:%s", "placedAt": "2026-11-19T09:31:12Z",
                                 "lines": [{"productNumber": "75423", "name": "LEGO X-Wing 75423", "quantity": 1}],
                                 "delivery": {"methodCode": "PARCEL_LOCKER",
                                              "location": {"kind": "PICKUP_POINT", "pointId": "KRA01M"}},
                                 "payment": {"methodCode": "PAY_DEFERRED"},
                                 "invoicing": {"buyerName": "Jan Kowalski", "nip": null,
                                               "address": {"street": "ul. Mineralna 5A", "city": "Kraków",
                                                           "postalCode": "30-001", "country": "PL"}}}"""
                                .formatted(System.nanoTime()))), "$.processingId"));
        as(HttpFixture.EMPLOYEE_SCOPE, HttpFixture.EMPLOYEE,
                post("/processing/" + processingId + "/assignment"));
        as(HttpFixture.EMPLOYEE_SCOPE, HttpFixture.EMPLOYEE,
                post("/processing/" + processingId + "/positions/75423").contentType(APPLICATION_JSON)
                        .content("{\"confirmation\": \"picked\"}"));
        as(HttpFixture.EMPLOYEE_SCOPE, HttpFixture.EMPLOYEE,
                post("/processing/" + processingId + "/packing").contentType(APPLICATION_JSON)
                        .content("{\"productNumbers\": [\"75423\"]}"));
        as(HttpFixture.EMPLOYEE_SCOPE, HttpFixture.EMPLOYEE,
                post("/processing/" + processingId + "/readiness"));
        return processingId;
    }

    private MockHttpServletResponse as(String scope, String caller, MockHttpServletRequestBuilder request)
            throws Exception {
        return HttpFixture.as(scope, caller, mvc, request);
    }

    @TestConfiguration
    static class CarrierStub {

        @Bean
        CarrierPort carrierPort() {
            return new FakeCarrierPort();
        }
    }

    @TestConfiguration
    static class WarehouseStub {

        @Bean
        WarehouseStock warehouseStock() {
            return new FakeWarehouseStock();
        }
    }
}
