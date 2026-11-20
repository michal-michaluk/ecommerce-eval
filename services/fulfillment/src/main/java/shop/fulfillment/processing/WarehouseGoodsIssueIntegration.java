package shop.fulfillment.processing;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

import shop.fulfillment.kernel.FulfillmentException;
import shop.fulfillment.kernel.IssueNote;
import shop.fulfillment.kernel.WarehouseStock;

/**
 * The third write register D11 names: the goods leave the warehouse when the order is declared ready.
 *
 * <p>{@code issue} changes what physically exists, so it is called once per reservation and only for a granted readiness —
 * a refusal and a replay never reach the warehouse. The reservation is addressed through
 * {@link ProcessingReservationRepository}, never through the aggregate: what a claim took is a boundary fact, and the order
 * that must not be widened carries none of it.
 *
 * <p>The note the warehouse answers is stored on the reservation row, so the document number and its date survive the
 * command that booked them out. A warehouse failure surfaced here fails the readiness itself (element 13 RULE-7): the order
 * is not left marked ready with no goods issue, because both writes are the one transaction the command owns.
 *
 * <p>A deployment with no warehouse adapter can still mark an order ready when it holds no reservation of ours; the moment a
 * reservation exists and nobody can answer {@link WarehouseStock}, the command is refused with {@code WAREHOUSE_NOT_WIRED}
 * instead of pretending the goods left.
 */
@Component
class WarehouseGoodsIssueIntegration {

    private final Optional<WarehouseStock> warehouse;
    private final ProcessingReservationRepository reservations;

    WarehouseGoodsIssueIntegration(Optional<WarehouseStock> warehouse,
                                   ProcessingReservationRepository reservations) {
        this.warehouse = warehouse;
        this.reservations = reservations;
    }

    /**
     * Books every reservation of one order out of the warehouse, once each.
     *
     * @param processing the processing whose readiness was granted
     */
    void issueClaim(OrderProcessing processing) {
        reservations.findFor(processing.processingId()).stream()
                .filter(reservation -> !reservation.issued())
                .forEach(this::issue);
    }

    private void issue(ProcessingReservation reservation) {
        IssueNote note = answeringWarehouse().issue(reservation.reservationId(), List.of(reservation.pickedItem()));
        reservations.record(reservation.issuedAs(note));
    }

    private WarehouseStock answeringWarehouse() {
        return warehouse.orElseThrow(() -> new FulfillmentException(
                FulfillmentException.Error.WAREHOUSE_NOT_WIRED, "issuing the goods of a ready order"));
    }
}
