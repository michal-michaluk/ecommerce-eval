package shop.fulfillment.processing;

import lombok.Builder;

import java.time.Instant;

/**
 * One observed change of what a location holds for a product.
 *
 * <p>The warehouse cannot notify anybody, so this is a fact we read and then raise ourselves; the same observation
 * delivered twice is a duplicate to collapse, not a second change.
 */
@Builder
public record StockChange(
        ProductId productNumber,
        LocationCode location,
        int available,
        int reserved,
        Instant observedAt) {

    public StockChange {
        if (productNumber == null || location == null || observedAt == null) {
            throw new FulfillmentException(FulfillmentException.Error.COMMAND_VALUE_MISSING, "stockChange");
        }
        if (available < 0 || reserved < 0) {
            throw new FulfillmentException(
                    FulfillmentException.Error.QUANTITY_NOT_POSITIVE, Math.min(available, reserved));
        }
    }
}
