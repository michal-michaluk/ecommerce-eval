package shop.fulfillment.kernel;

import java.util.List;

/**
 * One page of stock observations, together with the watermark the next read starts from.
 *
 * <p>Re-polling is safe: the port is a read, and nothing about the page is an event.
 */
public record StockChangePage(StockCursor nextCursor, List<StockChange> changes) {

    public StockChangePage {
        if (nextCursor == null || changes == null) {
            throw new FulfillmentException(FulfillmentException.Error.COMMAND_VALUE_MISSING, "stockChangePage");
        }
        changes = List.copyOf(changes);
    }
}
