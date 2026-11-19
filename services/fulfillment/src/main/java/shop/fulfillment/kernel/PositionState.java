package shop.fulfillment.kernel;

/**
 * The state of one position — the transcript's own words: a position is packed once it is in the box, and the state
 * never regresses.
 */
public enum PositionState {
    PENDING,
    PICKED,
    PACKED
}
