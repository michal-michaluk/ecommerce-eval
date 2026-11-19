package shop.fulfillment.processing;

import java.util.ArrayList;
import java.util.List;

import shop.fulfillment.kernel.FulfillmentException;
import shop.fulfillment.kernel.LocationCode;
import shop.fulfillment.kernel.OrderPosition;
import shop.fulfillment.kernel.ProductId;
import shop.fulfillment.kernel.ReservationId;
import shop.fulfillment.kernel.StockAtLocation;
import shop.fulfillment.kernel.WarehouseStock;

/**
 * The allocations one claim holds at the warehouse, and the only place anything releases them.
 *
 * <p>Register D11 puts the reservation on the granted claim, so the ids are minted here and kept here: the aggregate carries
 * no reservation and must not be widened to, and an allocation nobody can address is an allocation nobody can release
 * (element 13 state S1, R-13-1). A reservation that fails half way leaves the units already taken back at the warehouse —
 * {@code release} is idempotent by contract, so a compensation never has to ask whether it ran.
 *
 * <p>The location a position is reserved at is this phase's own decision: D11 names the trigger and no source names a
 * location, so the first location the warehouse reports with enough free units is taken (recorded in
 * {@code docs/phase-1-decisions.md}; element 10 owns location choice when the pick list lands).
 */
class WarehouseReservations {

    private final WarehouseStock warehouse;
    private final List<ReservationId> taken = new ArrayList<>();

    private WarehouseReservations(WarehouseStock warehouse) {
        this.warehouse = warehouse;
    }

    static WarehouseReservations on(WarehouseStock warehouse) {
        return new WarehouseReservations(warehouse);
    }

    /**
     * Reserves every position of a claimed order, or nothing at all.
     *
     * @param positions the positions the claim took
     */
    void reserveEvery(List<OrderPosition> positions) {
        try {
            positions.forEach(this::reserve);
        } catch (RuntimeException failure) {
            releaseAll();
            throw failure;
        }
    }

    /** Gives every allocation of this claim back; safe to call whether or not the claim was committed. */
    void releaseAll() {
        taken.forEach(warehouse::release);
    }

    private void reserve(OrderPosition position) {
        ProductId product = new ProductId(position.productNumber());
        taken.add(warehouse.reserve(product, stockedLocation(product, position.quantity()), position.quantity()));
    }

    private LocationCode stockedLocation(ProductId product, int quantity) {
        return warehouse.stockAt(product).stream()
                .filter(place -> place.available() >= quantity)
                .map(StockAtLocation::code)
                .findFirst()
                .orElseThrow(() -> new FulfillmentException(
                        FulfillmentException.Error.STOCK_OBSERVATION_MISSING, product.value()));
    }
}
