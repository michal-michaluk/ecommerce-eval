package shop.fulfillment.processing;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import shop.fulfillment.kernel.ReservationId;
import shop.fulfillment.kernel.StockCursor;

/**
 * The two pieces of state the warehouse boundary owns, backed by maps: the reservation correlation (element 13 S1) and the
 * stock-change watermark. A unit test asserts the translation without a database.
 */
class FakeWarehouseState implements WarehouseReservationRepository, StockCursorRepository {

    final Map<String, WarehouseReservation> reservations = new LinkedHashMap<>();
    final Map<String, String> forgotten = new LinkedHashMap<>();
    StockCursor cursor;

    @Override
    public Optional<WarehouseReservation> find(ReservationId reservationId) {
        return Optional.ofNullable(reservations.get(reservationId.value()));
    }

    @Override
    public void record(WarehouseReservation reservation) {
        reservations.put(reservation.reservationId().value(), reservation);
    }

    @Override
    public void forget(ReservationId reservationId) {
        forgotten.put(reservationId.value(), reservationId.value());
        reservations.remove(reservationId.value());
    }

    @Override
    public Optional<StockCursor> read() {
        return Optional.ofNullable(cursor);
    }

    @Override
    public void write(StockCursor cursor) {
        this.cursor = cursor;
    }
}
