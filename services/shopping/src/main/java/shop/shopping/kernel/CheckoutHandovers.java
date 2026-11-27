package shop.shopping.kernel;

/**
 * Consumer-side contract of the checkout handover: what the basket makes the checkout slice do, asked in the basket's own
 * language (element 09, D32).
 *
 * <p>The port is written in the basket slice's vocabulary and lives in the shared kernel, the one package both slices may
 * read: the checkout slice implements it, the basket slice injects it and never sees a checkout type (DoD25). Its signature is
 * value objects only — the payload and the receipt, never a checkout service, projection or entity.
 *
 * <p>It is a command, not an event: {@code go to checkout} is the terminal action of the basket screen, so the basket must
 * learn whether checkout took it — an event cannot refuse and its publisher never learns the outcome
 * ({@code mapping.event-cannot-refuse}).
 *
 * <p>The answer is an acceptance and nothing more. This port cannot express "I accepted it and your write failed": the basket
 * asks before it stores, so a lost write leaves checkout holding a handover the basket never recorded, and the only thing that
 * keeps that harmless is the receiver keying a repeat on {@link Handover#contentDigest()} rather than on the revision alone.
 * Confirming the pairing atomically — one acceptance per order, one order per acceptance — is a precondition of D4's checkout.
 */
public interface CheckoutHandovers {

    /**
     * Hands the selection over and answers what checkout accepted. The basket stays active and editable whatever the answer
     * (HAND-6), and checkout creates no order here (HAND-3).
     *
     * @param handover the payload checkout receives — the selection, its prices, the totals and the delivery range
     * @return the receipt: the handover's identity, the revision it carried and checkout's own identity for it
     */
    CheckoutHandover accept(Handover handover);
}
