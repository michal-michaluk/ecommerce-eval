package shop.fulfillment.processing;

import java.util.Optional;

import shop.fulfillment.kernel.StockCursor;

/**
 * Where the stock-change read stopped — element 13 state S1's sibling: the warehouse has no subscription and no memory of
 * what we already read, so the watermark is ours to keep.
 *
 * <p>An empty answer is the boundary's first poll: nothing has been read yet, and the caller chooses where reading starts
 * instead of replaying the warehouse's whole history.
 */
interface StockCursorRepository {

    Optional<StockCursor> read();

    void write(StockCursor cursor);
}
