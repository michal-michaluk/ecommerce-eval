package shop.fulfillment.kernel;

/**
 * The state of one position — the transcript's own words: a position is packed once it is in the box, and the state
 * never regresses.
 */
public enum PositionState {
    PENDING,
    PICKED,
    PACKED;

    /**
     * The state as the contract of element 11 spells it: the pre-scan state has no name in any source and the spec
     * settles it to {@code awaitingScan} (element 02, R-16-1), so the one vocabulary lives here instead of in each
     * adapter that renders a position.
     *
     * @return the wire name of this state
     */
    public String wireName() {
        return switch (this) {
            case PENDING -> "awaitingScan";
            case PICKED -> "picked";
            case PACKED -> "packed";
        };
    }
}
