/**
 * The parcel slice — the mutable carriage half of the context: the shipment of one processing is registered before
 * anything physical exists, the waybill the carrier returns is the only carrier identity it will ever hold, and every
 * leg it reaches is a fact of the {@link shop.fulfillment.parcel.ParcelEditor} aggregate.
 *
 * <p>The slice owns the parcel record alone; it reads the processing id it carries, our reference, the packing
 * sequence, the legs and the events as they are, from {@code shop.fulfillment.kernel}, and it never sees the
 * processing slice.
 */
package shop.fulfillment.parcel;
