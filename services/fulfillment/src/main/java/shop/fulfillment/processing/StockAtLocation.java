package shop.fulfillment.processing;

/**
 * How many units of one product are free, and how many are allocated, at one warehouse location — two independent
 * sums that are never derived from each other.
 */
public record StockAtLocation(LocationCode code, int available, int reserved) {

    public StockAtLocation {
        if (code == null) {
            throw new FulfillmentException(FulfillmentException.Error.LOCATION_CODE_MALFORMED, code);
        }
        if (available < 0 || reserved < 0) {
            throw new FulfillmentException(
                    FulfillmentException.Error.QUANTITY_NOT_POSITIVE, Math.min(available, reserved));
        }
    }
}
