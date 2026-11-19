/**
 * The issue dashboard of element 17 — the resolver's one screen: the open warehouse-reality gaps, grouped by the order each
 * one holds, answering {@code GET /issues}.
 *
 * <p>It is a read slice like {@code orders} and {@code customer}: it owns no aggregate, imports no write model and reaches
 * the processing only through the domain events and the shared kernel, so the read path is physically unable to report or
 * close an issue — closing belongs to the resume command of the processing resource (register D8).
 */
package shop.fulfillment.issues;
