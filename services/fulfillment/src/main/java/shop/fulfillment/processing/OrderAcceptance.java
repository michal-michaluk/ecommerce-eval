package shop.fulfillment.processing;

/**
 * What the intake did with one handover: the processing that now exists, and whether this delivery was the first one.
 *
 * <p>A repeated {@code orderHash} answers the processing that already exists with {@code duplicate = true} — no second
 * position set, no second parcel and no second event (element 12 RULE-2, register D4).
 *
 * @param processing the processing as the wire sees it
 * @param duplicate  whether the order had already been taken into processing before this delivery
 */
record OrderAcceptance(OrderProcessing processing, boolean duplicate) {
}
