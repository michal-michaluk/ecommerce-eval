package shop.fulfillment.processing;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import shop.fulfillment.kernel.IssueNote;
import shop.fulfillment.kernel.LocationCode;
import shop.fulfillment.kernel.PickedItem;
import shop.fulfillment.kernel.ProductId;
import shop.fulfillment.kernel.ReservationId;
import shop.fulfillment.kernel.StockAtLocation;
import shop.fulfillment.kernel.StockChangePage;
import shop.fulfillment.kernel.StockCursor;
import shop.fulfillment.kernel.WarehouseFixture;
import shop.fulfillment.kernel.WarehouseStock;

/**
 * The warehouse port as a test double: one shelf with a fixed quantity, every allocation recorded by product and by id, every
 * release recorded, and a failure the test can place on any reserve call — so a test proves both the happy claim and that a
 * warehouse failure takes the allocations already made back. It is public because the e2e harness wires it as the application's
 * warehouse bean (spec D14: the port plus an in-test fake, no HTTP).
 */
public class FakeWarehouseStock implements WarehouseStock {

    static final String SHELF = "A-01-01";

    final Map<String, Integer> allocated = new LinkedHashMap<>();
    final Map<String, Integer> released = new LinkedHashMap<>();
    final List<String> calls = new ArrayList<>();
    private int available = 10;
    private int failingFromReserve = Integer.MAX_VALUE;
    private RuntimeException failure;

    FakeWarehouseStock withAvailable(int quantity) {
        this.available = quantity;
        return this;
    }

    FakeWarehouseStock failingOnReserve(RuntimeException cause) {
        this.failure = cause;
        this.failingFromReserve = 1;
        return this;
    }

    FakeWarehouseStock failingOnReserveNumber(int number, RuntimeException cause) {
        this.failure = cause;
        this.failingFromReserve = number;
        return this;
    }

    @Override
    public List<StockAtLocation> stockAt(ProductId productId) {
        calls.add("stockAt " + productId.value());
        return List.of(WarehouseFixture.stock(SHELF, available, 0));
    }

    @Override
    public ReservationId reserve(ProductId productId, LocationCode location, int quantity) {
        calls.add("reserve " + productId.value() + " " + quantity);
        failOnReserveCallNumber(reservationsTaken());
        allocated.merge(productId.value(), quantity, Integer::sum);
        return new ReservationId("RSV-%04d".formatted(calls.size()));
    }

    @Override
    public void release(ReservationId reservationId) {
        calls.add("release " + reservationId.value());
        released.merge(reservationId.value(), 1, Integer::sum);
        allocated.replaceAll((product, quantity) -> 0);
    }

    @Override
    public IssueNote issue(ReservationId reservationId, List<PickedItem> picked) {
        calls.add("issue " + reservationId.value());
        return WarehouseFixture.anIssueNote();
    }

    @Override
    public StockChangePage stockChanges(StockCursor cursor) {
        calls.add("stockChanges " + cursor.value());
        return WarehouseFixture.anEmptyPage();
    }

    private int reservationsTaken() {
        return (int) calls.stream().filter(call -> call.startsWith("reserve")).count();
    }

    private void failOnReserveCallNumber(int callNumber) {
        if (callNumber >= failingFromReserve) {
            throw failure;
        }
    }
}
