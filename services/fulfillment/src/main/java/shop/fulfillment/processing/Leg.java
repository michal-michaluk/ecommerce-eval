package shop.fulfillment.processing;

/**
 * The six delivery legs every carrier is read in, in our words: L1 registered, L2 provider collected, L3 assigned for
 * pickup, L4 picked up, L5 in the sorting facility, L6 customer picked it.
 *
 * <p>L1 and L2 are the carrier acknowledging our own actions; L3–L6 are carrier-owned facts we cannot compute. A
 * carrier's own status codes live and die in its adapter and never reach this type.
 */
enum Leg {
    L1(LegState.PARCEL_REGISTERED),
    L2(LegState.PARCEL_READY),
    L3(LegState.ASSIGNED_TO_PICKUP),
    L4(LegState.PICKED_UP),
    L5(LegState.IN_SORTING_FACILITY),
    L6(LegState.DELIVERED_TO_CUSTOMER);

    private final LegState legState;

    Leg(LegState legState) {
        this.legState = legState;
    }

    /** The parcel state this leg reaches; states are ordered exactly as the legs are. */
    LegState legState() {
        return legState;
    }
}
