package shop.shopping.basket;

import java.util.Arrays;
import java.util.List;

import shop.shopping.basket.BasketConfiguration.OfferFixture;
import shop.shopping.basket.BasketConfiguration.OfferFixture.SeededOffer;
import shop.shopping.basket.BasketConfiguration.OfferFixture.StockAnswer;
import shop.shopping.kernel.Money;

/**
 * D67's seed as tests build it: one offer of the deployment's configuration document, the catalog those offers make, and the
 * two offers the contract's own sequence needs beyond a plain one — a row that runs out of stock after some reads and an offer
 * the provider has withdrawn while still describing it.
 *
 * <p>Both sides of the seed are here because they are one fact stated twice: {@link #aSeededOffer} is the YAML a deployment
 * writes ({@code basket.offer-fixture.offers}), {@link #anOnSaleSeed} is what the wiring makes of it, and the contract's step
 * 15 depends on the first surviving the second unchanged. The currencies stay lower-case on the settings side on purpose — a
 * deployment may write {@code pln}, and normalising it is the wiring's job, not the document's.
 */
public class OfferSeedFixture {

    public static final String SELLER = BasketFixture.UGREEN;
    public static final String A_LOWER_CASE_CURRENCY = "pln";
    public static final long LIST_PRICE = 69900;
    public static final long CURRENT_PRICE = 64990;
    public static final long NO_DISCOUNT = 0;

    /** The price and the discount the seed states, as the row carries them: a charge and a discount of one currency (D40). */
    public static final Money A_CURRENT_PRICE_IN_PLN = new Money(CURRENT_PRICE, BasketFixture.PLN);
    public static final Money NO_DISCOUNT_IN_PLN = new Money(NO_DISCOUNT, BasketFixture.PLN, Money.Kind.discount);

    private static final int ONE_READ = 1;

    private OfferSeedFixture() {
    }

    /**
     * The {@code basket.offer-fixture.*} block one deployment states.
     *
     * @param offers the seeded offers, in the order the document lists them
     * @return the block as it binds
     */
    public static OfferFixture aFixtureOf(SeededOffer... offers) {
        return new OfferFixture(List.of(offers));
    }

    /**
     * One offer of that block, answering {@code stock} for {@code reads} reads and no units from the next read on.
     *
     * @param offerId the offer the deployment seeds
     * @param stock   the warehouse's answer for the first reads, a count or {@code notRead}
     * @param reads   how many reads it holds for, absent for one
     * @return the seeded offer as it binds
     */
    public static SeededOffer aSeededOffer(String offerId, String stock, Integer reads) {
        return new SeededOffer(offerId, SELLER, "Fixture offer " + offerId, "https://fixture.invalid/" + offerId + ".png",
                LIST_PRICE, CURRENT_PRICE, NO_DISCOUNT, A_LOWER_CASE_CURRENCY, null,
                List.of(new StockAnswer(stock, reads), new StockAnswer("0", null)));
    }

    /**
     * One offer of that block that names no title — the seed that describes no row and must fail the pod, not step 3.
     *
     * @param offerId the offer the deployment seeds
     * @return the seeded offer as it binds
     */
    public static SeededOffer aSeededOfferWithoutTitle(String offerId) {
        return new SeededOffer(offerId, SELLER, null, "https://fixture.invalid/" + offerId + ".png",
                LIST_PRICE, CURRENT_PRICE, NO_DISCOUNT, BasketFixture.PLN, null,
                List.of(new StockAnswer(String.valueOf(ONE_READ), null)));
    }

    /**
     * The catalog a deployment's seed makes, as the port answers from it.
     *
     * @param offerings the offers the wiring converted
     * @return the adapter, seeded, with the queue each offer was given
     */
    public static FixtureOfferCatalog aSeededCatalog(FixtureOfferCatalog.Offering... offerings) {
        return new FixtureOfferCatalog(OfferCatalogFixture.aClock(), List.of(offerings));
    }

    /**
     * An offer on sale that answers the given units, one per read, the last repeating — the queue step 15 reads.
     *
     * @param offerId      the offer the deployment seeds
     * @param unitsPerRead the warehouse's answer for each read, in order
     * @return the offering the adapter is seeded with
     */
    public static FixtureOfferCatalog.Offering anOnSaleSeed(String offerId, int... unitsPerRead) {
        List<OfferObservation.Stock> answers = Arrays.stream(unitsPerRead)
                .mapToObj(units -> (OfferObservation.Stock) new OfferObservation.Stock.Quantity(units))
                .toList();
        return new FixtureOfferCatalog.Offering(offerId, OfferCatalogFixture.aDescription(),
                OfferObservation.OfferState.onSale, answers);
    }

    /**
     * An offer the provider withdrew while still describing it — the row step 20 greys and prices, never a {@code 404}.
     *
     * @param offerId the offer the deployment seeds
     * @return the offering the adapter is seeded with
     */
    public static FixtureOfferCatalog.Offering aDescribedRemoval(String offerId) {
        return new FixtureOfferCatalog.Offering(offerId, OfferCatalogFixture.aDescription(),
                OfferObservation.OfferState.removed, List.of(OfferObservation.Stock.NotRead.notRead));
    }
}
