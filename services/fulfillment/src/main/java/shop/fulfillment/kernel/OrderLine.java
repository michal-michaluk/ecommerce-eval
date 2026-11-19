package shop.fulfillment.kernel;

import java.util.List;

/**
 * One ordered product: its number, the name the pick list shows and the integer quantity bought.
 *
 * <p>A line that cannot be read refuses the whole handover as {@code ORDER_INCOMPLETE} and names the part of the payload that
 * has to be fixed, because that is the one refusal element 12 gives a rejected order and the one the client can act on
 * (element 12 RULE-1, element 11's {@code 422} row).
 */
public record OrderLine(String productNumber, String name, int quantity) {

    public OrderLine {
        if (productNumber == null || productNumber.isBlank()) {
            throw new FulfillmentException(FulfillmentException.Error.ORDER_INCOMPLETE,
                    List.of("lines.productNumber"));
        }
        if (name == null || name.isBlank()) {
            throw new FulfillmentException(FulfillmentException.Error.ORDER_INCOMPLETE, List.of("lines.name"));
        }
        if (quantity < 1) {
            throw new FulfillmentException(FulfillmentException.Error.ORDER_INCOMPLETE, List.of("lines.quantity"));
        }
    }
}
