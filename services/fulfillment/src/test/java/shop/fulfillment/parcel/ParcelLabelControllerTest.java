package shop.fulfillment.parcel;

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
import shop.fulfillment.processing.FakeWarehouseStock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Element 06 over the real chain: the carrier's document is served as {@code application/pdf}, a reprint is the document we
 * already stored — the carrier is asked once — and a parcel that does not exist yet answers a refusal rather than an empty
 * page.
 *
 * <p>It drives the routes of the wire, so the parcel it prints is the one a scan registered, exactly as a warehouse employee
 * would have produced it.
 */
@IntegrationTest
class ParcelLabelControllerTest {

    @Autowired
    WebApplicationContext context;
    @Autowired
    CarrierPort carrier;

    MockMvc mvc;

    @BeforeEach
    void buildsMockMvc() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void servesTheCarriersDocumentAsAPdf() throws Exception {
        String processingId = scannedProcessing();

        MockHttpServletResponse response = label(processingId);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getContentType()).contains("application/pdf");
        assertThat(response.getContentAsByteArray()).isEqualTo(FakeCarrierPort.DOCUMENT);
    }

    @Test
    void servesTheStoredDocumentOnAReprintWithoutAskingTheCarrierAgain() throws Exception {
        String processingId = scannedProcessing();
        long renderedBefore = rendered();

        label(processingId);
        MockHttpServletResponse reprint = label(processingId);

        assertThat(reprint.getContentAsByteArray()).isEqualTo(FakeCarrierPort.DOCUMENT);
        assertThat(rendered() - renderedBefore).isEqualTo(1);
    }

    @Test
    void answersAnUnknownProcessingWithTheContractsRefusal() throws Exception {
        MockHttpServletResponse response = label(assignedProcessing());

        assertThat(response.getStatus()).isEqualTo(404);
        assertThat(HttpFixture.json(response, "$.error")).isEqualTo("PROCESSING_NOT_FOUND");
    }

    @Test
    void refusesTheCustomerScope() throws Exception {
        String processingId = scannedProcessing();

        assertThat(HttpFixture.as(HttpFixture.CUSTOMER_SCOPE, HttpFixture.CUSTOMER, mvc,
                get("/processing/" + processingId + "/documents/label")).getStatus()).isEqualTo(403);
    }

    private MockHttpServletResponse label(String processingId) throws Exception {
        return HttpFixture.as(HttpFixture.EMPLOYEE_SCOPE, HttpFixture.EMPLOYEE, mvc,
                get("/processing/" + processingId + "/documents/label"));
    }

    private long rendered() {
        return carrier instanceof FakeCarrierPort fake ? fake.callsStartingWith("label").size() : 0;
    }

    private String assignedProcessing() throws Exception {
        String processingId = String.valueOf(HttpFixture.json(
                handover("sha256:" + System.nanoTime()), "$.processingId"));
        as(HttpFixture.EMPLOYEE_SCOPE, HttpFixture.EMPLOYEE,
                post("/processing/" + processingId + "/assignment"));
        return processingId;
    }

    private String scannedProcessing() throws Exception {
        String processingId = assignedProcessing();
        as(HttpFixture.EMPLOYEE_SCOPE, HttpFixture.EMPLOYEE,
                post("/processing/" + processingId + "/positions/75423").contentType(APPLICATION_JSON)
                        .content("{\"confirmation\": \"picked\"}"));
        as(HttpFixture.EMPLOYEE_SCOPE, HttpFixture.EMPLOYEE,
                post("/processing/" + processingId + "/packing").contentType(APPLICATION_JSON)
                        .content("{\"productNumbers\": [\"75423\"]}"));
        return processingId;
    }

    private MockHttpServletResponse handover(String orderHash) throws Exception {
        return as(HttpFixture.SHOPPING_SCOPE, HttpFixture.SHOPPING, post("/orders").contentType(APPLICATION_JSON)
                .content("""
                        {"orderHash": "%s", "placedAt": "2026-11-19T09:31:12Z",
                         "lines": [{"productNumber": "75423", "name": "LEGO X-Wing 75423", "quantity": 1}],
                         "delivery": {"methodCode": "PARCEL_LOCKER",
                                      "location": {"kind": "PICKUP_POINT", "pointId": "KRA01M"}},
                         "payment": {"methodCode": "PAY_DEFERRED"},
                         "invoicing": {"buyerName": "Jan Kowalski", "nip": null,
                                       "address": {"street": "ul. Mineralna 5A", "city": "Kraków",
                                                   "postalCode": "30-001", "country": "PL"}}}""".formatted(orderHash)));
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
