/**
 * The checkout slice — the second vertical slice of this context, and the receiver of the basket's terminal action
 * (element 09, D5).
 *
 * <p>It owns what happens after the customer decided: today that is the handover itself — accepting one payload per basket,
 * discarding a revision older than the one it holds (D35) and answering the receipt — and nothing else. The order it will
 * create (HAND-3), the price difference it will report against the basket's snapshot (D31, HAND-5) and the payment and
 * delivery methods it will validate belong to its own specification (D4); until that lands, this slice implements the shared
 * contract and no other port.
 *
 * <p>It reads {@code shop.shopping.kernel} — the handover payload, its estimate and the one exception — and it never sees a
 * basket type: the basket slice is package-private and the ArchUnit split keeps this slice closed in both directions (DoD25).
 */
package shop.shopping.checkout;
