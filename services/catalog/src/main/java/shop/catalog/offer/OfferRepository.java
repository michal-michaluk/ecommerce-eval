package shop.catalog.offer;

import java.util.Optional;

/**
 * The one secondary port of the offer aggregate: the adapter rehydrates the editor and saves it back, whichever storage shape it
 * uses (persistence-adapters.md).
 */
interface OfferRepository {

    Optional<OfferEditor> get(String offerId);

    void save(OfferEditor offer);
}
