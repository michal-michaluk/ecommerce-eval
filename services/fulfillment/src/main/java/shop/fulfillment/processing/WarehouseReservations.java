package shop.fulfillment.processing;

import java.util.ArrayList;
import java.util.List;

import shop.fulfillment.kernel.FulfillmentException;
import shop.fulfillment.kernel.LocationCode;
import shop.fulfillment.kernel.OrderPosition;
import shop.fulfillment.kernel.OrderProcessingId;
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
 * <p>Every allocation is handed back to the caller, which writes it to {@link ProcessingReservationRepository}: readiness books
 * the goods out against that correlation afterwards, and the ids alone would die with the claim's command (register D11's
 * {@code issue} trigger). What this class decides is what a claim takes — the persistence of it is the adapter's business, so a
 * model type here never names a repository.
 *
 * <p>The location a position is reserved at is this phase's own decision: D11 names the trigger and no source names a
 * location, so the first location the warehouse reports with enough free units is taken (recorded in
 * {@code docs/phase-1-decisions.md}; element 10 owns location choice when the pick list lands).
 */
class WarehouseReservations {

    private final WarehouseStock warehouse;
    private final List<ProcessingReservation> taken = new ArrayList<>();

    private WarehouseReservations(WarehouseStock warehouse) {
        this.warehouse = warehouse;
    }

    static WarehouseReservations on(WarehouseStock warehouse) {
        return new WarehouseReservations(warehouse);
    }

    /**
     * Reserves every position of a claimed order, or nothing at all.
     *
     * @param processingId the claim that takes them
     * @param positions    the positions the claim took
     * @return what was taken, one entry per position — the correlation the readiness command books the goods out against
     */
    List<ProcessingReservation> reserveEvery(OrderProcessingId processingId, List<OrderPosition> positions) {
        try {
            positions.forEach(position -> reserve(processingId, position));
        } catch (RuntimeException failure) {
            releaseAll();
            throw failure;
        }
        return List.copyOf(taken);
    }

    /** Gives every allocation of this claim back; safe to call whether or not the claim was committed. */
    void releaseAll() {
        taken.forEach(reservation -> warehouse.release(reservation.reservationId()));
    }

    private void reserve(OrderProcessingId processingId, OrderPosition position) {
        ProductId product = new ProductId(position.productNumber());
        LocationCode location = stockedLocation(product, position.quantity());
        ReservationId reservationId = warehouse.reserve(product, location, position.quantity());
        taken.add(ProcessingReservation.takenFor(processingId, product, location, position.quantity(), reservationId));
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
