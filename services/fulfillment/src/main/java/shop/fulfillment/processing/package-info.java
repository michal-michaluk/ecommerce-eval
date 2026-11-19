/**
 * The fulfillment bounded context — one finished, immutable {@link shop.fulfillment.processing.Order} becomes a
 * delivered parcel: an employee claims it exclusively, a pick list walks the warehouse, every position is confirmed
 * by scanning, the parcel is registered with a carrier before it exists, and every delivery state and warehouse
 * issue is a fact of the {@link shop.fulfillment.processing.OrderProcessing} aggregate.
 *
 * <p>The context owns the processing, the parcel record and the decisions around them; it owns neither the order
 * content, nor the stock, nor the carrier's own data. Those cross the boundary through
 * {@link shop.fulfillment.processing.WarehouseStock} and {@link shop.fulfillment.processing.CarrierPort}.
 */
package shop.fulfillment.processing;
