package shop.fulfillment.processing;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import java.time.LocalDate;
import java.util.List;

import shop.fulfillment.kernel.FulfillmentException;
import shop.fulfillment.kernel.IssueNote;
import shop.fulfillment.kernel.PickedItem;
import shop.fulfillment.kernel.ProductId;
import shop.fulfillment.kernel.ReservationId;
import shop.fulfillment.kernel.WarehouseFixture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * What the adapter writes: a reservation allocated against a transfer order and correlated in our own state, the release
 * that gives it back, and the goods issue that answers one note and books the goods out once.
 *
 * <p>The correlation is asserted on the store itself ({@code state.reservations}) on purpose: it <em>is</em> the fact this
 * boundary owns and the port hands nothing but an id back, so the store is the observable answer, not a stand-in for one.
 */
class InvenTreeWarehouseReservationTest extends InvenTreeWarehouseTest {

    static final ReservationId RESERVATION = new ReservationId("RSV-0001");

    @Test
    void reservesThroughATransferOrderAndKeepsTheCorrelation() {
        givenAShelfThatIsAskedAbout();
        transferOrder();
        transferOrderLine(3);
        allocate();
        allocations();

        ReservationId reservationId = client.reserve(new ProductId(InvenTreePayloadFixture.PRODUCT),
                InvenTreePayloadFixture.SHELF, 3);

        assertThat(reservationId.value()).startsWith("RSV-");
        assertThat(state.reservations.values()).singleElement().satisfies(reservation -> {
            assertThat(reservation.orderPk()).isEqualTo(7);
            assertThat(reservation.allocationPks()).containsExactly(21);
            assertThat(reservation.issued()).isFalse();
        });
        warehouse.verify();
    }

    @Test
    void refusesToReserveWhatTheShelvesDoNotHold() {
        givenAShelfThatIsAskedAbout();
        transferOrder();
        transferOrderLine(99);

        assertThatThrownBy(() -> client.reserve(new ProductId(InvenTreePayloadFixture.PRODUCT),
                InvenTreePayloadFixture.SHELF, 99))
                .isInstanceOfSatisfying(FulfillmentException.class, refused -> assertThat(refused.error())
                        .isEqualTo(FulfillmentException.Error.WAREHOUSE_STOCK_SHORT));
    }

    @Test
    void releasesEveryAllocationOfAKnownReservation() {
        state.record(WarehouseAdapterFixture.aReservation(7, 21, 22));
        allowReleaseOf(21);
        allowReleaseOf(22);

        client.release(RESERVATION);

        assertThat(state.forgotten).containsKey(RESERVATION.value());
        assertThat(state.reservations).isEmpty();
        warehouse.verify();
    }

    @Test
    void refusesToReleaseAReservationWhoseGoodsAlreadyLeft() {
        state.record(WarehouseAdapterFixture.aReservation(7, 21).issuedAs("RSV-0001", LocalDate.parse("2026-11-19")));

        assertThatThrownBy(() -> client.release(RESERVATION))
                .isInstanceOfSatisfying(FulfillmentException.class, refused -> assertThat(refused.error())
                        .isEqualTo(FulfillmentException.Error.WAREHOUSE_ALLOCATION_CONSUMED));
        warehouse.verify();
    }

    @Test
    void releasesNothingForAReservationItNeverTook() {
        client.release(new ReservationId("RSV-NOBODY"));

        warehouse.verify();
    }

    @Test
    void booksTheGoodsOutOnceAndAnswersTheSameNoteAgain() {
        state.record(WarehouseAdapterFixture.aReservation(7, 21));
        locations();
        warehouse.expect(requestTo(InvenTreePayloadFixture.URL + "/api/stock/?limit=10&part=75423&location=3"))
                .andRespond(InvenTreePayloadFixture.page(InvenTreePayloadFixture.STOCK_PAGE));
        warehouse.expect(requestTo(InvenTreePayloadFixture.URL + "/api/stock/remove/"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess());

        IssueNote first = client.issue(RESERVATION, List.of(aPickedItem(2)));
        IssueNote repeated = client.issue(RESERVATION, List.of(aPickedItem(2)));

        assertThat(first.documentNumber()).isEqualTo(RESERVATION.value());
        assertThat(first.date()).isEqualTo(LocalDate.parse("2026-11-19"));
        assertThat(repeated).isEqualTo(first);
        warehouse.verify();
    }

    @Test
    void booksEveryPickedItemOutInTheOneRemovalRequest() {
        state.record(WarehouseAdapterFixture.aReservation(7, 21));
        locations();
        warehouse.expect(requestTo(InvenTreePayloadFixture.URL + "/api/stock/?limit=10&part=75423&location=3"))
                .andRespond(InvenTreePayloadFixture.page(InvenTreePayloadFixture.STOCK_PAGE));
        locations();
        warehouse.expect(requestTo(InvenTreePayloadFixture.URL + "/api/stock/?limit=10&part=99881&location=3"))
                .andRespond(InvenTreePayloadFixture.page(InvenTreePayloadFixture.ORCHID_PAGE));
        warehouse.expect(requestTo(InvenTreePayloadFixture.URL + "/api/stock/remove/"))
                .andExpect(jsonPath("$.items[0].pk").value(41))
                .andExpect(jsonPath("$.items[0].quantity").value("2"))
                .andExpect(jsonPath("$.items[1].pk").value(51))
                .andExpect(jsonPath("$.items[1].quantity").value("1"))
                .andRespond(withSuccess());

        client.issue(RESERVATION, List.of(aPickedItem(2), anOrchid()));

        warehouse.verify();
    }

    @Test
    void refusesToIssueAgainstAReservationItNeverTook() {
        assertThatThrownBy(() -> client.issue(new ReservationId("RSV-NOBODY"), List.of(aPickedItem(1))))
                .isInstanceOfSatisfying(FulfillmentException.class, refused -> assertThat(refused.error())
                        .isEqualTo(FulfillmentException.Error.WAREHOUSE_RESERVATION_UNKNOWN));
    }

    /** The two reads a reservation starts with: the location list, then the stock items at that location. */
    private void givenAShelfThatIsAskedAbout() {
        locations();
        warehouse.expect(requestTo(InvenTreePayloadFixture.URL + "/api/stock/?limit=10&part=75423&location=3"))
                .andRespond(InvenTreePayloadFixture.page(InvenTreePayloadFixture.STOCK_PAGE));
    }

    private void transferOrder() {
        warehouse.expect(requestTo(InvenTreePayloadFixture.URL + "/api/order/transfer-order/"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.reference").value(startsWith("RSV-")))
                .andExpect(jsonPath("$.description").value("fulfillment picking reservation"))
                .andRespond(withSuccess("""
                        {"pk": 7, "reference": "fulfillment"}""", org.springframework.http.MediaType.APPLICATION_JSON));
    }

    private void transferOrderLine(int quantity) {
        warehouse.expect(requestTo(InvenTreePayloadFixture.URL + "/api/order/transfer-order-line/"))
                .andExpect(jsonPath("$.order").value(7))
                .andExpect(jsonPath("$.part").value(75423))
                .andExpect(jsonPath("$.quantity").value(String.valueOf(quantity)))
                .andRespond(withSuccess("""
                        {"pk": 11}""", org.springframework.http.MediaType.APPLICATION_JSON));
    }

    private void allocate() {
        warehouse.expect(requestTo(InvenTreePayloadFixture.URL + "/api/order/transfer-order/7/allocate/"))
                .andExpect(jsonPath("$.items[0].lineItem").value(11))
                .andExpect(jsonPath("$.items[0].stockItem").value(41))
                .andExpect(jsonPath("$.items[0].quantity").value("3"))
                .andRespond(withSuccess());
    }

    private void allocations() {
        warehouse.expect(requestTo(InvenTreePayloadFixture.URL
                + "/api/order/transfer-order-allocation/?limit=10&order=7"))
                .andRespond(InvenTreePayloadFixture.page("""
                        {"count": 1, "results": [{"pk": 21, "order": 7, "item": 41, "quantity": 3.0}]}"""));
    }

    private static PickedItem aPickedItem(int quantity) {
        return WarehouseFixture.aPickedItemOf(new ProductId(InvenTreePayloadFixture.PRODUCT),
                InvenTreePayloadFixture.SHELF, quantity);
    }

    private static PickedItem anOrchid() {
        return WarehouseFixture.aPickedItemOf(new ProductId(InvenTreePayloadFixture.ORCHID),
                InvenTreePayloadFixture.SHELF, 1);
    }
}
