package shop.fulfillment.kernel;

import java.util.List;

/**
 * One position of the processing: exactly one per order line, created at intake, with the line's quantity and the
 * product name the pick list shows (element 02 RULE-3).
 */
public record OrderPosition(String productNumber, String name, int quantity, PositionState state) {

    public OrderPosition {
        if (productNumber == null || productNumber.isBlank()) {
            throw new FulfillmentException(FulfillmentException.Error.PRODUCT_NUMBER_MALFORMED, productNumber);
        }
        if (quantity < 1) {
            throw new FulfillmentException(FulfillmentException.Error.QUANTITY_NOT_POSITIVE, quantity);
        }
        if (state == null) {
            throw new FulfillmentException(FulfillmentException.Error.ORDER_INCOMPLETE, List.of("positions.state"));
        }
    }

    /** The same position, confirmed by a scan. */
    public OrderPosition picked() {
        return new OrderPosition(productNumber, name, quantity, PositionState.PICKED);
    }

    /** The same position, in the box. */
    public OrderPosition packed() {
        return new OrderPosition(productNumber, name, quantity, PositionState.PACKED);
    }
}
