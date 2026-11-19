package shop.fulfillment.processing;

/**
 * What one claim did to the processing.
 *
 * <p>{@code granted} is read from the transition the aggregate made, never recomputed from the decision table: a claim that
 * changed the assignee was granted, a repeated claim by the same employee is the no-op of element 07 DT-07-4, and a refused
 * claim never reaches this record at all.
 *
 * @param processing the processing as the wire sees it
 * @param granted    whether this claim took the order
 */
record OrderClaim(OrderProcessing processing, boolean granted) {
}
