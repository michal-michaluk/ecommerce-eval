package shop.fulfillment.processing;

/**
 * The id of one warehouse allocation, minted by us — never a warehouse id, never parsed from a URL, never guessed.
 *
 * <p>The mapping from it to the warehouse's own allocation rows is the boundary's own state: the allocation call does
 * not return them, so they are re-read and correlated behind the port.
 */
public record ReservationId(String value) {

    public ReservationId {
        if (value == null || value.isBlank()) {
            throw new FulfillmentException(FulfillmentException.Error.COMMAND_VALUE_MISSING, "reservationId");
        }
    }
}
