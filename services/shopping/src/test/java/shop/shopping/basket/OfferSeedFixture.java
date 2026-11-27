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

    private static final List<StockAnswer> AN_ANSWER = List.of(new StockAnswer("9", null));

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
        return new SeededOffer(offerId, SELLER, null, aPictureOf(offerId),
                LIST_PRICE, CURRENT_PRICE, NO_DISCOUNT, BasketFixture.PLN, null, AN_ANSWER);
    }

    /**
     * One offer of that block that names no seller — the field RULE-7 groups the row into a shipment by, so a seed without it
     * describes no row either.
     *
     * @param offerId the offer the deployment seeds
     * @return the seeded offer as it binds
     */
    public static SeededOffer aSeededOfferWithoutSeller(String offerId) {
        return new SeededOffer(offerId, null, aTitleOf(offerId), aPictureOf(offerId),
                LIST_PRICE, CURRENT_PRICE, NO_DISCOUNT, A_LOWER_CASE_CURRENCY, null, AN_ANSWER);
    }

    /**
     * One offer of that block that names no picture — a row renders one, so an offer that names none cannot answer it.
     *
     * @param offerId the offer the deployment seeds
     * @return the seeded offer as it binds
     */
    public static SeededOffer aSeededOfferWithoutPicture(String offerId) {
        return new SeededOffer(offerId, SELLER, aTitleOf(offerId), null,
                LIST_PRICE, CURRENT_PRICE, NO_DISCOUNT, A_LOWER_CASE_CURRENCY, null, AN_ANSWER);
    }

    /**
     * One offer of that block that names no list price — the one amount no row may hold yet every offer must state (D53).
     *
     * @param offerId the offer the deployment seeds
     * @return the seeded offer as it binds
     */
    public static SeededOffer aSeededOfferWithoutListPrice(String offerId) {
        return new SeededOffer(offerId, SELLER, aTitleOf(offerId), aPictureOf(offerId),
                null, CURRENT_PRICE, NO_DISCOUNT, A_LOWER_CASE_CURRENCY, null, AN_ANSWER);
    }

    /**
     * One offer of that block that names no currency — the one property every amount of the offer is read in.
     *
     * @param offerId the offer the deployment seeds
     * @return the seeded offer as it binds
     */
    public static SeededOffer aSeededOfferWithoutCurrency(String offerId) {
        return new SeededOffer(offerId, SELLER, aTitleOf(offerId), aPictureOf(offerId),
                LIST_PRICE, CURRENT_PRICE, NO_DISCOUNT, null, null, AN_ANSWER);
    }

    /**
     * One offer of that block whose title is stated but empty — present in the document, describing nothing.
     *
     * @param offerId the offer the deployment seeds
     * @return the seeded offer as it binds
     */
    public static SeededOffer aSeededOfferWithABlankTitle(String offerId) {
        return new SeededOffer(offerId, SELLER, "  ", aPictureOf(offerId),
                LIST_PRICE, CURRENT_PRICE, NO_DISCOUNT, A_LOWER_CASE_CURRENCY, null, AN_ANSWER);
    }

    /**
     * One offer of that block whose queue holds an answer for no read at all — a count that can never be handed out.
     *
     * @param offerId the offer the deployment seeds
     * @return the seeded offer as it binds
     */
    public static SeededOffer aSeededOfferReadZeroTimes(String offerId) {
        return new SeededOffer(offerId, SELLER, aTitleOf(offerId), aPictureOf(offerId),
                LIST_PRICE, CURRENT_PRICE, NO_DISCOUNT, A_LOWER_CASE_CURRENCY, null,
                List.of(new StockAnswer("9", 0)));
    }

    /**
     * One offer of that block whose warehouse answered {@code notRead} — the read that says nothing about stock (A5).
     *
     * @param offerId the offer the deployment seeds
     * @return the seeded offer as it binds
     */
    public static SeededOffer aSeededOfferWhoseStockWasNeverRead(String offerId) {
        return new SeededOffer(offerId, SELLER, aTitleOf(offerId), aPictureOf(offerId),
                LIST_PRICE, CURRENT_PRICE, NO_DISCOUNT, A_LOWER_CASE_CURRENCY, null,
                List.of(new StockAnswer(OfferObservation.Stock.NotRead.notRead.name(), null)));
    }

    /**
     * One offer of that block that states no queue — an offer whose stock nobody reads at all (A5).
     *
     * @param offerId the offer the deployment seeds
     * @return the seeded offer as it binds
     */
    public static SeededOffer aSeededOfferWithoutAnswers(String offerId) {
        return new SeededOffer(offerId, SELLER, aTitleOf(offerId), aPictureOf(offerId),
                LIST_PRICE, CURRENT_PRICE, NO_DISCOUNT, A_LOWER_CASE_CURRENCY, null, null);
    }

    /**
     * One offer of that block whose queue is stated but empty — an offer nobody read either.
     *
     * @param offerId the offer the deployment seeds
     * @return the seeded offer as it binds
     */
    public static SeededOffer aSeededOfferWithAnEmptyAnswerQueue(String offerId) {
        return new SeededOffer(offerId, SELLER, aTitleOf(offerId), aPictureOf(offerId),
                LIST_PRICE, CURRENT_PRICE, NO_DISCOUNT, A_LOWER_CASE_CURRENCY, null, List.of());
    }

    /**
     * One entry of that block that names no offer at all — a row of the document that matches nothing the provider reads.
     *
     * @return the seeded offer as it binds
     */
    public static SeededOffer aSeededOfferWithoutId() {
        return new SeededOffer(null, SELLER, "Fixture offer without an id", "https://fixture.invalid/no-id.png",
                LIST_PRICE, CURRENT_PRICE, NO_DISCOUNT, A_LOWER_CASE_CURRENCY, null, AN_ANSWER);
    }

    private static String aTitleOf(String offerId) {
        return "Fixture offer " + offerId;
    }

    private static String aPictureOf(String offerId) {
        return "https://fixture.invalid/" + offerId + ".png";
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
