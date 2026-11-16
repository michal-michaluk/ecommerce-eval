package shop.catalog.offer;

import java.util.HashMap;
import java.util.Map;

/** Which offer already carries which identifier — the population fact the aggregate asks the {@link OfferRegistry} for (RULE-11). */
class FakeOfferRegistry implements OfferRegistry {

    private final Map<String, String> productNumbers = new HashMap<>();
    private final Map<String, String> eanGtins = new HashMap<>();

    FakeOfferRegistry withProductNumber(String productNumber, String offerId) {
        productNumbers.put(productNumber, offerId);
        return this;
    }

    FakeOfferRegistry withEanGtin(String eanGtin, String offerId) {
        eanGtins.put(eanGtin, offerId);
        return this;
    }

    @Override
    public boolean isProductNumberTakenByOther(String productNumber, String offerId) {
        return isTakenByOther(productNumbers.get(productNumber), offerId);
    }

    @Override
    public boolean isEanGtinTakenByOther(String eanGtin, String offerId) {
        return isTakenByOther(eanGtins.get(eanGtin), offerId);
    }

    private boolean isTakenByOther(String owner, String offerId) {
        return owner != null && !owner.equals(offerId);
    }
}
