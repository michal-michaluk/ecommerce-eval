package shop.shopping.basket;

import java.util.List;

/**
 * Element 08: the secondary port the basket learns an offer through — what it is, what it costs and whether it can be sold.
 *
 * <p>Package-private on purpose (D57): the basket declares it and an adapter in the same slice implements it, so nothing
 * outside {@code shop.shopping.basket} may name it and no other context gains a contract it never signed. It is a secondary
 * port, not a contract port — the aggregate is the only caller, and the fixture behind it in this increment (D38) is
 * replaced by the browsing slice's adapter without this interface moving.
 *
 * <p>One batched read, never one call per row: a basket with twenty items must not make twenty calls on a page render
 * (element 08 §Port). There is no cache and no timer here — freshness is event-driven (Q23, D25) and the caller decides when
 * to ask.
 *
 * <p>The answer holds one {@link OfferRecord} per offer the provider answered about, and the three ways of not answering stay
 * distinguishable, because the basket's reaction to each is different (element 08 §Failure mapping):
 *
 * <ul>
 *   <li>an id a {@code 200} omitted is answered as {@code removed} — the adapter knows the provider answered and left the
 *       offer out, and "the offer is gone" is a fact the application must not have to derive (A3, D33);</li>
 *   <li>a read that failed — {@code 401}/{@code 403}, a timeout, a {@code 5xx} — is answered as {@code notRead} for every
 *       requested id, never as a missing record and never as an exception: a failed read greys a row, it never fails the
 *       basket (A4, D26);</li>
 *   <li>a call the provider refused as malformed ({@code 404}) is answered with nothing at all, because the call was wrong
 *       and the offer was not: the basket keeps what it already knows.</li>
 * </ul>
 *
 * <p>No reservation operation exists on this port and none may be added (R6, DoD17). {@code reserveProductQuantity} belongs to
 * fulfilment, is explicitly not idempotent, and putting it on the basket's read path is the leak element 08
 * §Recommendations 4 exists to prevent — an observation is answered by an observation, never by a hold on a warehouse.
 */
interface OfferCatalog {

    /**
     * Reads every named offer in one call.
     *
     * @param offerIds the offers the basket holds, in the order of its rows; nothing to read asks nothing of the provider
     * @return one record per offered id the provider answered about — empty when it refused the call itself
     */
    List<OfferRecord> read(List<String> offerIds);
}
