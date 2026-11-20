package shop.fulfillment.kernel;

import java.util.List;

/**
 * How the customer pays — an opaque method code this context never enumerates (element 01 RULE-5).
 *
 * <p>No amount, currency or rounding appears here: money belongs to checkout and to the invoicing boundary.
 */
public record Payment(String methodCode) {

    public Payment {
        if (methodCode == null || methodCode.isBlank()) {
            throw new FulfillmentException(FulfillmentException.Error.ORDER_INCOMPLETE, List.of("payment.methodCode"));
        }
    }
}
