package shop.shopping.basket;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.stream.IntStream;

import shop.shopping.kernel.Money;

/**
 * Element 08's read record as tests build it: the offer the capture holds, the same offer read again an hour later, the two
 * ways a read answers nothing, and the twenty ids a batched read is proved on.
 *
 * <p>Every record is built here rather than inline, because a test should read in the vocabulary of its own row — {@code
 * anOfferOnSaleWithStock(0)} says which A-rule it belongs to — and because the two prices a record carries are the pair D53
 * keeps apart: {@link #LIST_PRICE} is what the catalog asks, {@link #CURRENT_PRICE} what the offer is sold at, and no row may
 * ever hold the first.
 */
public class OfferCatalogFixture {

    public static final String OFFER = BasketFixture.FIRST_OFFER;
    public static final String ANOTHER_OFFER = BasketFixture.SECOND_OFFER;
    public static final String SELLER = BasketFixture.UGREEN;

    public static final Instant READ_AT = BasketFixture.NOW;
    public static final Instant AN_EARLIER_READ_AT = READ_AT.minusSeconds(3_600);
    public static final Instant A_LATER_READ_AT = READ_AT.plusSeconds(3_600);

    public static final int SOME_UNITS = 12;
    public static final int ONE_UNIT = 1;
    public static final int NO_UNITS = 0;
    public static final int A_NEGATIVE_COUNT = -1;

    public static final long LIST_PRICE = 109890;
    public static final long CURRENT_PRICE = 99890;
    public static final long DISCOUNT = 10000;

    /** The two amounts the fixture provider states, each with the kind a row must carry (element 03 RULE-6, D40). */
    public static final Money A_CHARGED_PRICE = new Money(CURRENT_PRICE, BasketFixture.PLN, Money.Kind.charge);
    public static final Money A_DISCOUNTED_AMOUNT = new Money(DISCOUNT, BasketFixture.PLN, Money.Kind.discount);

    private static final int A_BASKET_FULL_OF_OFFERS = 20;

    private OfferCatalogFixture() {
    }

    public static Clock aClock() {
        return Clock.fixed(READ_AT, ZoneOffset.UTC);
    }

    public static FixtureOfferCatalog aCatalog() {
        return new FixtureOfferCatalog(aClock());
    }

    public static OfferRecord anOfferOnSaleWithStock(int units) {
        return anOfferOnSaleWithStockReadAt(units, READ_AT);
    }

    public static OfferRecord anOfferOnSaleWithStockReadAt(int units, Instant readAt) {
        return aRecord(OFFER, aDescription(), OfferObservation.OfferState.onSale, units, readAt);
    }

    public static OfferRecord anOfferWhoseStockWasNotRead() {
        return new OfferRecord(OFFER, aDescription(), OfferObservation.OfferState.onSale,
                OfferObservation.Stock.NotRead.notRead, READ_AT);
    }

    public static OfferRecord anUnreadOfferDescribing(OfferRecord.OfferDescription description) {
        return new OfferRecord(OFFER, description, OfferObservation.OfferState.notRead,
                OfferObservation.Stock.NotRead.notRead, READ_AT);
    }

    public static OfferRecord aWithdrawnOffer() {
        return new OfferRecord(OFFER, null, OfferObservation.OfferState.removed,
                OfferObservation.Stock.NotRead.notRead, READ_AT);
    }

    public static OfferRecord aFailedRead() {
        return aFailedReadReadAt(READ_AT);
    }

    public static OfferRecord aFailedReadReadAt(Instant readAt) {
        return new OfferRecord(OFFER, null, OfferObservation.OfferState.notRead,
                OfferObservation.Stock.NotRead.notRead, readAt);
    }

    public static OfferRecord anOfferOf(String offerId, OfferObservation.OfferState offerState, Instant readAt) {
        return aRecord(offerId, aDescription(), offerState, SOME_UNITS, readAt);
    }

    public static OfferRecord anOfferSoldBy(String seller) {
        return new OfferRecord(OFFER, aDescriptionSoldBy(seller), OfferObservation.OfferState.onSale,
                new OfferObservation.Stock.Quantity(SOME_UNITS), READ_AT);
    }

    /** The same offer answered in another currency — the observation D63 keeps the last-known prices for. */
    public static OfferRecord anOfferPricedIn(String currency) {
        return anOfferPricedInFrom(currency, SELLER);
    }

    /** The same, in another currency and from another seller: a shipment is not a price, so D63 keeps it. */
    public static OfferRecord anOfferPricedInFrom(String currency, String seller) {
        return new OfferRecord(OFFER, aDescriptionIn(currency, seller), OfferObservation.OfferState.onSale,
                new OfferObservation.Stock.Quantity(SOME_UNITS), READ_AT);
    }

    public static OfferRecord.OfferDescription aDescriptionIn(String currency) {
        return aDescriptionIn(currency, SELLER);
    }

    public static OfferRecord.OfferDescription aDescriptionIn(String currency, String seller) {
        OfferRecord.OfferDescription described = aDescription();
        return new OfferRecord.OfferDescription(seller, described.title(), described.pictureUrl(),
                new Money(LIST_PRICE, currency), new Money(CURRENT_PRICE, currency),
                new Money(DISCOUNT, currency, Money.Kind.discount));
    }

    public static OfferRecord withoutTitle() {
        return new OfferRecord(OFFER, aDescriptionWithoutATitle(), OfferObservation.OfferState.onSale,
                new OfferObservation.Stock.Quantity(SOME_UNITS), READ_AT);
    }

    public static OfferObservationCommand aPriceChangeOf(String offerId, Instant readAt, long currentPrice) {
        return new OfferObservationCommand.Refreshed(offerId, readAt,
                new OfferObservationCommand.OfferSnapshot(null, null,
                        new Money(currentPrice, BasketFixture.PLN, Money.Kind.charge), null));
    }

    public static OfferRecord withoutASeller(String offerId) {
        return new OfferRecord(offerId, aDescriptionWithoutASeller(), OfferObservation.OfferState.onSale,
                new OfferObservation.Stock.Quantity(SOME_UNITS), READ_AT);
    }

    public static OfferRecord.OfferDescription aDescription() {
        return new OfferRecord.OfferDescription(SELLER, "UGREEN Nexode 65W", "img-1001",
                new Money(LIST_PRICE, BasketFixture.PLN), new Money(CURRENT_PRICE, BasketFixture.PLN),
                new Money(DISCOUNT, BasketFixture.PLN, Money.Kind.discount));
    }

    public static OfferRecord.OfferDescription aDescriptionSoldBy(String seller) {
        OfferRecord.OfferDescription described = aDescription();
        return new OfferRecord.OfferDescription(seller, described.title(), described.pictureUrl(), described.price(),
                described.currentPrice(), described.discount());
    }

    public static OfferRecord.OfferDescription aDescriptionWithoutATitle() {
        OfferRecord.OfferDescription described = aDescription();
        return new OfferRecord.OfferDescription(described.seller(), null, described.pictureUrl(),
                described.price(), described.currentPrice(), described.discount());
    }

    public static OfferRecord.OfferDescription aDescriptionWithoutASeller() {
        OfferRecord.OfferDescription described = aDescription();
        return new OfferRecord.OfferDescription(null, described.title(), described.pictureUrl(), described.price(),
                described.currentPrice(), described.discount());
    }

    public static OfferRecord.OfferDescription aDescriptionPricedIn(Money currentPrice) {
        OfferRecord.OfferDescription described = aDescription();
        return new OfferRecord.OfferDescription(described.seller(), described.title(), described.pictureUrl(),
                described.price(), currentPrice, described.discount());
    }

    public static OfferObservationCommand.OfferSnapshot aSnapshotPricedInTwoCurrencies() {
        return new OfferObservationCommand.OfferSnapshot(null, null, A_CHARGED_PRICE,
                new Money(DISCOUNT, "EUR", Money.Kind.discount));
    }

    public static BasketItem.LastKnown aSnapshotOfTheAnswer() {
        return new BasketItem.LastKnown("UGREEN Nexode 65W", "img-1001", A_CHARGED_PRICE, A_DISCOUNTED_AMOUNT);
    }

    public static List<String> anOfferIdPerRow() {
        return IntStream.range(0, A_BASKET_FULL_OF_OFFERS)
                .mapToObj(row -> "OF-" + (2_000 + row))
                .toList();
    }

    public static OfferObservationCommand aCommandOf(OfferRecord record) {
        return OfferObservationCommand.of(record);
    }

    private static OfferRecord aRecord(String offerId, OfferRecord.OfferDescription description,
            OfferObservation.OfferState offerState, int units, Instant readAt) {
        return new OfferRecord(offerId, description, offerState, new OfferObservation.Stock.Quantity(units), readAt);
    }
}
