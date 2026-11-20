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
import shop.fulfillment.kernel.CarrierPort;
import shop.fulfillment.kernel.OrderProcessingId;
import shop.fulfillment.kernel.WarehouseStock;
import shop.fulfillment.parcel.FakeCarrierPort;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Element 08 over the real chain: a granted readiness answers the parcel the order now carries, a refusal answers
 * {@code 409 NOT_READY} with the decision's own reasons, a replay books no second collection, and the scope matrix keeps the
 * customer out of a command it has no business issuing.
 */
@IntegrationTest
class ReadinessControllerTest {

    @Autowired
    WebApplicationContext context;
    @Autowired
    OrderIntakeService orders;
    @Autowired
    AssignmentService assignments;
    @Autowired
    ProcessingService work;
    @Autowired
    CarrierPort carrier;
    @Autowired
    WarehouseStock stock;
    @Autowired
    ProcessingReservationRepository reservations;

    MockMvc mvc;

    @BeforeEach
    void buildsMockMvc() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void marksAPackedOrderReadyWithCarrierAndParcel() throws Exception {
        String processingId = packedProcessing();

        MockHttpServletResponse response = readiness(processingId);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(HttpFixture.json(response, "$.state")).isEqualTo("readyForDelivery");
        assertThat(HttpFixture.json(response, "$.parcel.waybill")).asString().isNotEmpty();
        assertThat(HttpFixture.json(response, "$.parcel.reference")).isEqualTo(processingId + "-1");
        assertThat(HttpFixture.json(response, "$.parcel.legState")).isEqualTo("parcelReady");
    }

    @Test
    void asksTheCarrierToCollectOnce() throws Exception {
        String processingId = packedProcessing();
        long collectedBefore = collections();

        readiness(processingId);
        MockHttpServletResponse repeated = readiness(processingId);

        assertThat(repeated.getStatus()).isEqualTo(200);
        assertThat(collections() - collectedBefore).isEqualTo(1);
    }

    @Test
    void refusesAnOrderThatIsNotPackedAndNamesItsReason() throws Exception {
        String processingId = scannedProcessing();

        MockHttpServletResponse response = readiness(processingId);

        assertThat(response.getStatus()).isEqualTo(409);
        assertThat(HttpFixture.json(response, "$.error")).isEqualTo("NOT_READY");
        assertThat(HttpFixture.json(response, "$.reasons[0]")).isEqualTo("notPacked");
        assertThat(collections()).isZero();
    }

    @Test
    void refusesAnOrderWithoutAParcelAndNamesItsReason() throws Exception {
        MockHttpServletResponse response = readiness(assignedProcessing());

        assertThat(response.getStatus()).isEqualTo(409);
        assertThat(HttpFixture.json(response, "$.reasons[0]")).isEqualTo("noParcel");
    }

    @Test
    void refusesTheCustomerScopeAndAnswersAnUnknownOrder() throws Exception {
        String processingId = packedProcessing();

        assertThat(HttpFixture.as(HttpFixture.CUSTOMER_SCOPE, HttpFixture.CUSTOMER, mvc,
                post("/processing/" + processingId + "/readiness")).getStatus()).isEqualTo(403);

        MockHttpServletResponse unknown = HttpFixture.as(HttpFixture.EMPLOYEE_SCOPE, HttpFixture.EMPLOYEE, mvc,
                post("/processing/OP-2026-9999/readiness"));

        assertThat(unknown.getStatus()).isEqualTo(404);
        assertThat(HttpFixture.json(unknown, "$.error")).isEqualTo("PROCESSING_NOT_FOUND");
    }

    private long collections() {
        return carrier instanceof FakeCarrierPort fake ? fake.callsStartingWith("requestCollection").size() : 0;
    }

    private MockHttpServletResponse readiness(String processingId) throws Exception {
        return HttpFixture.as(HttpFixture.EMPLOYEE_SCOPE, HttpFixture.EMPLOYEE, mvc,
                post("/processing/" + processingId + "/readiness"));
    }

    @Test
    void booksTheGoodsOutOnceAtReadinessAndKeepsTheNote() throws Exception {
        String processingId = packedProcessing();
        int issuedBefore = warehouse().issuesTaken();

        readiness(processingId);
        readiness(processingId);

        assertThat(warehouse().issuesTaken() - issuedBefore).isEqualTo(1);
        assertThat(reservations.findFor(new OrderProcessingId(processingId))).singleElement().satisfies(reservation -> {
            assertThat(reservation.issued()).isTrue();
            assertThat(reservation.issueDocument()).isEqualTo("WZ-2026-0001");
        });
    }

    @Test
    void keepsTheOrderUnreadyAndRecordsNothingWhenTheWarehouseCannotIssue() {
        String processingId = packedProcessing();
        warehouse().failingOnIssue(new IllegalStateException("removal refused"));

        assertThatThrownBy(() -> work.markReady(processingId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("removal refused");
        assertThat(reservations.findFor(new OrderProcessingId(processingId)))
                .allSatisfy(reservation -> assertThat(reservation.issued()).isFalse());
    }

    private FakeWarehouseStock warehouse() {
        return (FakeWarehouseStock) stock;
    }

    private String assignedProcessing() {
        String processingId = orders.accept(OrderHandoverFixture.aHandover()).processing().processingId().value();
        assignments.claim(processingId, HttpFixture.EMPLOYEE);
        return processingId;
    }

    private String packedProcessing() {
        String processingId = scannedProcessing();
        work.pack(processingId, List.of("75423"), HttpFixture.EMPLOYEE);
        return processingId;
    }

    private String scannedProcessing() {
        String processingId = assignedProcessing();
        work.pick(processingId, "75423", HttpFixture.EMPLOYEE);
        return processingId;
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
