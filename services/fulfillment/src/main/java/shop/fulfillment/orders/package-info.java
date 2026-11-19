/**
 * The orders list of element 15 — the one screen the warehouse works from: every current order with its processing state,
 * who holds it and how far its positions got, fed by the events of the order processing slice and answering {@code GET
 * /orders}.
 *
 * <p>It is a read slice: it owns no aggregate, imports no write model and reaches the processing only through the domain
 * events and the shared kernel, so the read path is physically unable to call a service or a repository of the write side
 * ({@code http.no-write-model-in-read}).
 */
package shop.fulfillment.orders;
