package shop.catalog.offer;

import java.util.List;
import java.util.Optional;

/**
 * The one secondary port of the offer aggregate: the adapter rehydrates the editor and saves it back, whichever storage shape it
 * uses (persistence-adapters.md).
 *
 * <p>{@link #findAll} and {@link #count} exist because 12 lists offers and publishes a version history, and element 17 gives neither
 * of them a read model: the offer resource is answered from the aggregate's own store, one page of editors rehydrated exactly as a
 * command rehydrates one.
 */
interface OfferRepository {

    Optional<OfferEditor> get(String offerId);

    List<OfferEditor> findAll(int page, int size);

    long count();

    void save(OfferEditor offer);
}
