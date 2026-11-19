package shop.fulfillment.kernel;

import java.util.List;

/**
 * One ordered product: its number, the name the pick list shows and the integer quantity bought.
 */
public record OrderLine(String productNumber, String name, int quantity) {

    public OrderLine {
        if (productNumber == null || productNumber.isBlank()) {
            throw new FulfillmentException(FulfillmentException.Error.PRODUCT_NUMBER_MALFORMED, productNumber);
        }
        if (name == null || name.isBlank()) {
            throw new FulfillmentException(FulfillmentException.Error.ORDER_INCOMPLETE, List.of("lines.name"));
        }
        if (quantity < 1) {
            throw new FulfillmentException(FulfillmentException.Error.QUANTITY_NOT_POSITIVE, quantity);
        }
    }
}
