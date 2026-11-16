package shop.catalog.pricing;

import java.util.Optional;

/** The persistence boundary of the pricing model: an editor rehydrated by offer id, and the editor saved back. */
interface PricingRepository {

    Optional<PricingModelEditor> get(String offerId);

    void save(PricingModelEditor pricingModel);
}
