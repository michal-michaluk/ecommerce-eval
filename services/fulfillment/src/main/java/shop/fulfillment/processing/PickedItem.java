package shop.fulfillment.processing;

/**
 * What a scan confirmed: one product, one location, one quantity — the unit of a goods issue.
 */
public record PickedItem(ProductId productNumber, LocationCode location, int quantity) {

    public PickedItem {
        if (productNumber == null || location == null) {
            throw new FulfillmentException(FulfillmentException.Error.COMMAND_VALUE_MISSING, "pickedItem");
        }
        if (quantity < 1) {
            throw new FulfillmentException(FulfillmentException.Error.QUANTITY_NOT_POSITIVE, quantity);
        }
    }
}
