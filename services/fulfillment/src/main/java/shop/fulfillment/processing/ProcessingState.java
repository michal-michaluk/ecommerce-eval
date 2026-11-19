package shop.fulfillment.processing;

/**
 * The state of one order processing — every state in the lane lives here, and it changes only through a command of
 * {@link OrderProcessingEditor}.
 *
 * <p>There is no edge from {@code PENDING} or {@code ASSIGNED} into {@link #ON_HOLD}, and none out of
 * {@link #DELIVERED}.
 */
enum ProcessingState {
    PENDING,
    ASSIGNED,
    PICKING,
    PACKED,
    READY_FOR_DELIVERY,
    ON_HOLD,
    DELIVERED;

    /** The state a confirmation of the first position leads to: {@code assigned} starts picking, nothing else moves. */
    ProcessingState afterPicking() {
        return this == ASSIGNED ? PICKING : this;
    }

    /** The state after a position went into the box: the order is packed once every position is. */
    ProcessingState afterPacking(boolean allPositionsPacked) {
        return allPositionsPacked ? PACKED : this;
    }
}
