package shop.fulfillment.kernel;

/**
 * The state of one parcel's delivery, ordered as the legs are: it moves forward only and an unknown carrier status
 * leaves it untouched.
 */
public enum LegState {
    PARCEL_REGISTERED,
    PARCEL_READY,
    ASSIGNED_TO_PICKUP,
    PICKED_UP,
    IN_SORTING_FACILITY,
    DELIVERED_TO_CUSTOMER;

    /**
     * The state as the contract of element 11 spells it and the element 03 examples write it — one vocabulary for the wire,
     * so no adapter invents its own spelling of a leg state.
     *
     * @return the wire name of this state
     */
    public String wireName() {
        return switch (this) {
            case PARCEL_REGISTERED -> "parcelRegistered";
            case PARCEL_READY -> "parcelReady";
            case ASSIGNED_TO_PICKUP -> "assignedToPickup";
            case PICKED_UP -> "pickedUp";
            case IN_SORTING_FACILITY -> "inSortingFacility";
            case DELIVERED_TO_CUSTOMER -> "deliveredToCustomer";
        };
    }
}
