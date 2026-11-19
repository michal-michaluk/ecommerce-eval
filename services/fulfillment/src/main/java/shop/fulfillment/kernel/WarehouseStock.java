package shop.fulfillment.kernel;

import java.util.List;

/**
 * Consumer-side contract: the warehouse as the picking process needs it.
 *
 * <p>Every signature is written in this context's business words; the protocol translation is total and one-way, and
 * no warehouse type — part, stock item or allocation primary key — ever appears here.
 *
 * <p>Three asymmetries are part of the contract, not of the adapter: {@code release} is idempotent while
 * {@code reserve} is not, {@code issue} answers the same note for a repeated call, and a failure is surfaced rather
 * than smoothed — no fallback quantity, no cached stock served as fresh, no invented retry.
 *
 * <p>The fifth operation exists because the warehouse cannot tell us anything: it has no outbound subscription, so a
 * change is something we read by watermark and raise ourselves.
 */
public interface WarehouseStock {

    /**
     * What the warehouse holds for one product, per location, with free and allocated units as two independent sums.
     *
     * @param productId the product to ask about
     * @return one entry per location that holds the product
     */
    List<StockAtLocation> stockAt(ProductId productId);

    /**
     * Allocates units at one location. Not idempotent: a repeated call reserves twice, so a caller never blind-retries.
     *
     * @param productId the product to allocate
     * @param location  the location to allocate from
     * @param quantity  how many units, at least one
     * @return the id we mint for this allocation
     */
    ReservationId reserve(ProductId productId, LocationCode location, int quantity);

    /**
     * Ends an allocation, bringing the units back. Idempotent by contract: releasing an already released reservation is
     * a no-op, never a "not found".
     *
     * @param reservationId the allocation to end
     */
    void release(ReservationId reservationId);

    /**
     * Books the goods out of the warehouse against an allocation. At most once per reservation: a repeat answers the
     * same note instead of issuing twice.
     *
     * @param reservationId the allocation being consumed
     * @param picked        what the scans confirmed
     * @return the goods issue note of this reservation
     */
    IssueNote issue(ReservationId reservationId, List<PickedItem> picked);

    /**
     * Reads what changed after a watermark. Idempotent and safe to re-poll; duplicates are the caller's to collapse.
     *
     * @param cursor where the previous read stopped
     * @return the next page of observations
     */
    StockChangePage stockChanges(StockCursor cursor);
}
