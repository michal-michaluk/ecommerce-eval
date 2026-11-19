package shop.fulfillment.processing;

/**
 * An opaque watermark into the warehouse's change stream: the boundary remembers where it read up to, the warehouse
 * only answers "what changed after this".
 */
public record StockCursor(String value) {

    public StockCursor {
        if (value == null) {
            throw new FulfillmentException(FulfillmentException.Error.COMMAND_VALUE_MISSING, "stockCursor");
        }
    }
}
