package shop.fulfillment.processing;

import java.util.List;

/**
 * The delivery method the customer paid for, together with the one place the parcel may go.
 */
record Delivery(String methodCode, DeliveryLocation location) {

    Delivery {
        if (methodCode == null || methodCode.isBlank()) {
            throw new FulfillmentException(FulfillmentException.Error.ORDER_INCOMPLETE, List.of("delivery.methodCode"));
        }
        if (location == null) {
            throw new FulfillmentException(FulfillmentException.Error.ORDER_INCOMPLETE, List.of("delivery.location"));
        }
    }
}
