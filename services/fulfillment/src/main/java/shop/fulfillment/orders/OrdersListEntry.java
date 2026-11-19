package shop.fulfillment.orders;

import java.time.Instant;

import shop.fulfillment.kernel.ProcessingState;

/**
 * One row of element 15's list: the processing reference, its state, who holds it, who receives the parcel, how far the
 * positions got and when the order was placed.
 *
 * <p>No money appears: no price, total, currency or VAT is shown or stored (spec D5). The hold is a marker on the state and
 * never a separate column, because the hold is the state itself (element 09 RULE-2).
 *
 * <p>An {@code assignee} nobody holds is on the wire as an explicit null rather than as an absent key: the list states who
 * holds an order in every row, and a client must not have to tell "unclaimed" from "field not served" (element 11:41).
 */
record OrdersListEntry(
        String processingId,
        String state,
        String assignee,
        String recipient,
        String positionProgress,
        Instant placedAt,
        boolean onHold) {

    /**
     * The row as one render shows it, or {@code null} when the stored row is not a row of this list at all — a claim that
     * arrived before its handover has no placement time and no recipient to show.
     */
    static OrdersListEntry ofNullable(OrdersListProjection.OrdersListRow row) {
        if (row.state() == null || row.placedAt() == null) {
            return null;
        }
        ProcessingState state = ProcessingState.valueOf(row.state());
        return new OrdersListEntry(row.processingId(), state.wireName(), row.assignee(), row.recipient(),
                progress(row), row.placedAt(), state == ProcessingState.ON_HOLD);
    }

    /** Element 15's own words: {@code n/m packed}. */
    private static String progress(OrdersListProjection.OrdersListRow row) {
        return row.positionsPacked() + "/" + row.positionsTotal() + " packed";
    }
}
