package shop.fulfillment.processing;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import shop.fulfillment.kernel.OrderProcessingId;

/**
 * The {@link ProcessingReservationRepository} port backed by a map: the same record-and-find lifecycle the adapter has, without a
 * database, so a unit test asserts the use case and not the storage.
 *
 * <p>It exists once, here, because three tests need it — the claim that writes the correlation, the readiness that books the goods
 * out against it and the store's own test — and three copies of the same eight lines are three places to forget. The read side
 * answers what was recorded, in the order it was recorded, which is what the adapter does.
 */
class FakeProcessingReservations implements ProcessingReservationRepository {

    final Map<String, ProcessingReservation> rows = new LinkedHashMap<>();

    @Override
    public void record(ProcessingReservation reservation) {
        rows.put(reservation.reservationId().value(), reservation);
    }

    @Override
    public List<ProcessingReservation> findFor(OrderProcessingId processingId) {
        return rows.values().stream()
                .filter(row -> row.processingId().equals(processingId))
                .toList();
    }

    List<ProcessingReservation> recorded() {
        return new ArrayList<>(rows.values());
    }
}
