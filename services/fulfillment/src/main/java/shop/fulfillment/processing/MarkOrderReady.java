package shop.fulfillment.processing;

/**
 * The declaration an employee makes when the order leaves the warehouse's hands — {@code POST
 * /processing/{processingId}/readiness}.
 *
 * <p>It carries nothing: the decision of element 08 reads the order's own state and the parcel's, and who may declare it is
 * the employee scope's. The record exists so the route states its command, and the body stays empty.
 */
record MarkOrderReady() {
}
