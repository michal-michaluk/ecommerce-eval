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
    DELIVERED_TO_CUSTOMER
}
