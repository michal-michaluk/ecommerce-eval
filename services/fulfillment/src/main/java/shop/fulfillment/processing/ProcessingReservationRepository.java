package shop.fulfillment.processing;

import java.util.List;

import shop.fulfillment.kernel.OrderProcessingId;

/**
 * The processing → reservation correlation, owned by this slice: which allocations a claim took for an order, and whether the
 * goods were already booked out for them.
 *
 * <p>It is the store register D11's third write trigger needs. The aggregate must not carry it — a reservation is a
 * warehouse fact and the order document is a business one — so the correlation lives here, beside the aggregate rather than
 * inside it, and readiness asks this port instead of a field.
 *
 * <p>Unlike {@link WarehouseReservationRepository}, which addresses the warehouse's own rows and therefore outlives our
 * transaction, these rows are written inside the claim's transaction: they describe a reservation we took, so a claim that
 * rolls back must not leave one behind.
 */
interface ProcessingReservationRepository {

    void record(ProcessingReservation reservation);

    List<ProcessingReservation> findFor(OrderProcessingId processingId);
}
