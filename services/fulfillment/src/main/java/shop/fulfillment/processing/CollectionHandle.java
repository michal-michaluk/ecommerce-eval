package shop.fulfillment.processing;

/**
 * The carrier's opaque booking handle for one collection. The parcel stores it so an adapter that offers to change or
 * cancel a booking has something to address.
 */
public record CollectionHandle(String value) {

    public CollectionHandle {
        if (value == null || value.isBlank()) {
            throw new FulfillmentException(FulfillmentException.Error.COMMAND_VALUE_MISSING, "collectionHandle");
        }
    }
}
