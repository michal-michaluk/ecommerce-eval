package shop.fulfillment.processing;

/**
 * The shop's product number, the id every warehouse and pick-list question is asked with.
 */
public record ProductId(String value) {

    public ProductId {
        if (value == null || value.isBlank()) {
            throw new FulfillmentException(FulfillmentException.Error.PRODUCT_NUMBER_MALFORMED, value);
        }
    }
}
