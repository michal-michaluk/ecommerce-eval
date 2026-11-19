/**
 * The customer slice — the separate resource of element 18: one row per processing, fed by the domain events of the lane and
 * by the delivery legs the carrier reported, answering the customer's own view of an order.
 *
 * <p>It is a read slice like {@code orders}: it imports the shared kernel and neither write slice, its state is its own
 * table, and the two facts a customer must never see — the assignee and the issue — are not columns at all, so no request of
 * this scope can reach them (D-11-4, element 11 RULE-4).
 */
package shop.fulfillment.customer;
