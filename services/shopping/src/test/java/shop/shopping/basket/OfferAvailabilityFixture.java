package shop.shopping.basket;

import java.time.Instant;
import java.util.stream.Stream;

/**
 * Element 06's interface scenarios I1–I7 as test data: the two answers of the catalog, the three answers of the warehouse, the
 * failed read of a known offer and the same read of an offer nobody ever read.
 *
 * <p>Every observation is built here rather than inline, because a test should read in the vocabulary of its own row —
 * {@code anOfferOnSaleWhoseStockWasNotRead()} says which rule it belongs to — and because {@link #everyObservation()} is the
 * whole input space the decision claims to be total over: both offer states of a readable offer and the failed one, a stock
 * count that is some and one that is none, the explicit non-answer, and two read instants an hour apart.
 */
public class OfferAvailabilityFixture {

    public static final Instant READ_AT = BasketFixture.NOW;
    public static final Instant AN_EARLIER_READ_AT = READ_AT.minusSeconds(3_600);
    public static final Instant A_LATER_READ_AT = READ_AT.plusSeconds(3_600);

    public static final int SOME_UNITS = 12;
    public static final int ONE_UNIT = 1;
    public static final int NO_UNITS = 0;
    public static final int A_NEGATIVE_COUNT = -1;

    private OfferAvailabilityFixture() {
    }

    public static OfferObservation anOfferOnSaleWithStock(int units) {
        return anOfferOnSaleWithStockReadAt(units, READ_AT);
    }

    public static OfferObservation anOfferOnSaleWithStockReadAt(int units, Instant readAt) {
        return anObservationOf(OfferObservation.OfferState.onSale, new OfferObservation.Stock.Quantity(units), readAt);
    }

    public static OfferObservation anOfferOnSaleWithoutStock() {
        return anOfferOnSaleWithStock(NO_UNITS);
    }

    public static OfferObservation anOfferOnSaleWhoseStockWasNotRead() {
        return anObservationOf(OfferObservation.OfferState.onSale, OfferObservation.Stock.NotRead.notRead, READ_AT);
    }

    public static OfferObservation aRemovedOffer() {
        return anObservationOf(OfferObservation.OfferState.removed, OfferObservation.Stock.NotRead.notRead, READ_AT);
    }

    public static OfferObservation aRemovedOfferWithStock(int units) {
        return anObservationOf(OfferObservation.OfferState.removed, new OfferObservation.Stock.Quantity(units), READ_AT);
    }

    public static OfferObservation aFailedRead() {
        return anObservationOf(OfferObservation.OfferState.notRead, OfferObservation.Stock.NotRead.notRead, READ_AT);
    }

    public static OfferObservation aFailedReadOf(BasketItem item) {
        return new OfferObservation(item.offerId(), OfferObservation.OfferState.notRead,
                OfferObservation.Stock.NotRead.notRead, READ_AT);
    }

    public static OfferObservation anObservationOf(OfferObservation.OfferState offerState, OfferObservation.Stock stock,
            Instant readAt) {
        return new OfferObservation(BasketFixture.FIRST_OFFER, offerState, stock, readAt);
    }

    public static Stream<OfferObservation> everyObservation() {
        return Stream.of(OfferObservation.OfferState.values())
                .flatMap(OfferAvailabilityFixture::observationsOf);
    }

    private static Stream<OfferObservation> observationsOf(OfferObservation.OfferState offerState) {
        return everyStock()
                .flatMap(stock -> Stream.of(AN_EARLIER_READ_AT, A_LATER_READ_AT)
                        .map(readAt -> anObservationOf(offerState, stock, readAt)));
    }

    public static Stream<OfferObservation.Stock> everyStock() {
        return Stream.of(new OfferObservation.Stock.Quantity(SOME_UNITS), new OfferObservation.Stock.Quantity(ONE_UNIT),
                new OfferObservation.Stock.Quantity(NO_UNITS), new OfferObservation.Stock.Quantity(A_NEGATIVE_COUNT),
                OfferObservation.Stock.NotRead.notRead);
    }
}
