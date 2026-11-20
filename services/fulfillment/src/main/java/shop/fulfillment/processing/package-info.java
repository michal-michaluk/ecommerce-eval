/**
 * The order fulfillment process slice — the mutable half of the context: one finished, immutable
 * {@link shop.fulfillment.kernel.Order} becomes a delivered parcel, an employee claims it exclusively, a pick list
 * walks the warehouse, every position is confirmed by scanning, and every delivery state and warehouse issue is a
 * fact of the {@link shop.fulfillment.processing.OrderFulfillmentProcess} aggregate.
 *
 * <p>The slice owns the processing, the decisions that move it and the walk that serves it; it reads the order
 * content, the positions, the issues, the delivery legs, the stock and the carrier contract as they are, from
 * {@code shop.fulfillment.kernel}, and it never sees the parcel slice — a parcel is a {@code ParcelId} and nothing
 * more here.
 */
package shop.fulfillment.processing;
