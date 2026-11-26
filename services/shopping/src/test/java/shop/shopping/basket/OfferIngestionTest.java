package shop.shopping.basket;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import shop.shopping.kernel.Availability;
import shop.shopping.kernel.Handover;
import shop.shopping.kernel.ShoppingException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static shop.shopping.basket.BasketFixture.BASKET_ID;
import static shop.shopping.basket.BasketFixture.FIRST_OFFER;
import static shop.shopping.basket.BasketFixture.aBasketCurrency;
import static shop.shopping.basket.BasketFixture.aBasketWith;
import static shop.shopping.basket.BasketFixture.aFirstItem;
import static shop.shopping.basket.BasketFixture.aLastKnown;
import static shop.shopping.basket.BasketFixture.aRowFromBeforeTheReadInstantWasKept;
import static shop.shopping.basket.BasketFixture.anotherBasketId;
import static shop.shopping.basket.BasketFixture.anArchivedBasketWith;
import static shop.shopping.basket.BasketFixture.anUnavailableItem;
import static shop.shopping.basket.OfferCatalogFixture.A_LATER_READ_AT;
import static shop.shopping.basket.OfferCatalogFixture.AN_EARLIER_READ_AT;
import static shop.shopping.basket.OfferCatalogFixture.ANOTHER_OFFER;
import static shop.shopping.basket.OfferCatalogFixture.CURRENT_PRICE;
import static shop.shopping.basket.OfferCatalogFixture.LIST_PRICE;
import static shop.shopping.basket.OfferCatalogFixture.OFFER;
import static shop.shopping.basket.OfferCatalogFixture.READ_AT;
import static shop.shopping.basket.OfferCatalogFixture.SOME_UNITS;
import static shop.shopping.basket.OfferCatalogFixture.aCatalog;
import static shop.shopping.basket.OfferCatalogFixture.A_CHARGED_PRICE;
import static shop.shopping.basket.OfferCatalogFixture.aCommandOf;
import static shop.shopping.basket.OfferCatalogFixture.aPriceChangeOf;
import static shop.shopping.basket.OfferCatalogFixture.A_DISCOUNTED_AMOUNT;
import static shop.shopping.basket.OfferCatalogFixture.aFailedRead;
import static shop.shopping.basket.OfferCatalogFixture.aSnapshotOfTheAnswer;
import static shop.shopping.basket.OfferCatalogFixture.aFailedReadReadAt;
import static shop.shopping.basket.OfferCatalogFixture.aWithdrawnOffer;
import static shop.shopping.basket.OfferCatalogFixture.anOfferOf;
import static shop.shopping.basket.OfferCatalogFixture.anOfferOnSaleWithStock;
import static shop.shopping.basket.OfferCatalogFixture.anOfferOnSaleWithStockReadAt;
import static shop.shopping.basket.OfferCatalogFixture.anOfferSoldBy;
import static shop.shopping.basket.OfferCatalogFixture.anOfferWhoseStockWasNotRead;
import static shop.shopping.basket.OfferCatalogFixture.withoutTitle;

/**
 * Element 10's application path in process (D39): one observation applied to one row, availability re-decided by element 06,
 * the snapshot replaced with what the provider said, and the four rules that make the path safe — an event never creates a row
 * (EVT-2), a read after an event still wins (EVT-3), a duplicate delivery costs nothing (EVT-4), and no observation is ever
 * answered with a reservation (EVT-6, DoD17).
 *
 * <p>The second half is D53: the row keeps the price it is sold at and the flattened discount with their kinds stated, and the
 * catalog's list price reaches nothing an aggregate or a line could read.
 */
class OfferIngestionTest {

    final FakeBasketRepository baskets = new FakeBasketRepository();
    final BasketService service = new BasketService(baskets, aBasketCurrency(), Clock.fixed(READ_AT, ZoneOffset.UTC));

    @Test
    void replacesTheRowsSnapshotWithThePriceItIsSoldAt() {
        baskets.save(aBasketWith(aFirstItem()));

        Basket observed = service.applyObservation(BASKET_ID, aCommandOf(anOfferOnSaleWithStockReadAt(3, A_LATER_READ_AT)));

        assertThat(observed.items()).singleElement().satisfies(item -> {
            assertThat(item.lastKnown().currentPrice()).isEqualTo(A_CHARGED_PRICE);
            assertThat(item.lastKnown().discount()).isEqualTo(A_DISCOUNTED_AMOUNT);
            assertThat(item.availability()).isEqualTo(Availability.available());
            assertThat(item.readAt()).isEqualTo(A_LATER_READ_AT);
        });
        assertThat(observed.revision()).isEqualTo(1);
        assertThat(baskets.emitted).isEmpty();
    }

    @Test
    void neverPutsTheCatalogsListPriceOnTheRow() {
        baskets.save(aBasketWith(aFirstItem()));

        Basket observed = service.applyObservation(BASKET_ID, aCommandOf(anOfferOnSaleWithStock(SOME_UNITS)));

        assertThat(observed.items()).singleElement().satisfies(item -> {
            assertThat(item.lastKnown().currentPrice().amount()).isEqualTo(CURRENT_PRICE);
            assertThat(item.lastKnown().currentPrice().amount()).isNotEqualTo(LIST_PRICE);
            assertThat(item.lastKnown()).isEqualTo(aSnapshotOfTheAnswer());
        });
    }

    @Test
    void keepsTheLastKnownSnapshotWhenTheReadFails() {
        baskets.save(aBasketWith(aFirstItem()));

        Basket observed = service.applyObservation(BASKET_ID, aCommandOf(aFailedReadReadAt(A_LATER_READ_AT)));

        assertThat(observed.items()).singleElement().satisfies(item -> {
            assertThat(item.availability()).isEqualTo(Availability.unreadable());
            assertThat(item.lastKnown()).isEqualTo(aLastKnown());
            assertThat(item.readAt()).isEqualTo(A_LATER_READ_AT);
        });
    }

    @Test
    void keepsABlockedRowInsteadOfRemovingIt() {
        baskets.save(aBasketWith(aFirstItem()));

        Basket observed = service.applyObservation(BASKET_ID, aCommandOf(aWithdrawnOffer()));

        assertThat(observed.items()).singleElement().satisfies(item -> {
            assertThat(item.availability()).isEqualTo(Availability.removed());
            assertThat(item.quantity()).isEqualTo(1);
            assertThat(item.lastKnown()).isEqualTo(aLastKnown());
        });
    }

    @Test
    void keepsTheRowOrderableWhenOnlyTheStockWasNotRead() {
        baskets.save(aBasketWith(aFirstItem()));

        Basket observed = service.applyObservation(BASKET_ID, aCommandOf(anOfferWhoseStockWasNotRead()));

        assertThat(observed.items()).singleElement()
                .satisfies(item -> assertThat(item.availability()).isEqualTo(Availability.availableWithoutStockRead()));
        assertThat(service.handOver(BASKET_ID).selection()).singleElement()
                .satisfies(line -> assertThat(line.offerId()).isEqualTo(FIRST_OFFER));
    }

    @Test
    void bringsAnUnavailableRowBack() {
        baskets.save(aBasketWith(anUnavailableItem()));

        Basket observed = service.applyObservation(BASKET_ID,
                aCommandOf(anOfferOf(anUnavailableItem().offerId(), OfferObservation.OfferState.onSale, A_LATER_READ_AT)));

        assertThat(observed.items()).singleElement()
                .satisfies(item -> assertThat(item.availability()).isEqualTo(Availability.available()));
    }

    @Test
    void ignoresAnObservationOfAnOfferTheBasketDoesNotHold() {
        baskets.save(aBasketWith(aFirstItem()));

        Basket observed = service.applyObservation(BASKET_ID,
                aCommandOf(anOfferOf(ANOTHER_OFFER, OfferObservation.OfferState.onSale, A_LATER_READ_AT)));

        assertThat(observed.items()).singleElement()
                .satisfies(item -> assertThat(item.offerId()).isEqualTo(FIRST_OFFER));
        assertThat(observed.revision()).isZero();
        assertThat(baskets.saved).containsExactly(BASKET_ID.value());
    }

    @Test
    void applyingTheSameObservationTwiceChangesNothing() {
        baskets.save(aBasketWith(aFirstItem()));
        OfferObservationCommand command = aCommandOf(anOfferOnSaleWithStock(SOME_UNITS));

        Basket once = service.applyObservation(BASKET_ID, command);
        Basket twice = service.applyObservation(BASKET_ID, command);

        assertThat(twice).isEqualTo(once);
        assertThat(baskets.saved).containsExactly(BASKET_ID.value(), BASKET_ID.value());
    }

    @Test
    void discardsAnObservationOlderThanTheRowsRead() {
        baskets.save(aBasketWith(aFirstItem()));
        Basket before = service.basket(BASKET_ID);

        Basket observed = service.applyObservation(BASKET_ID,
                aCommandOf(anOfferOnSaleWithStockReadAt(0, AN_EARLIER_READ_AT)));

        assertThat(observed).isEqualTo(before);
        assertThat(baskets.saved).containsExactly(BASKET_ID.value());
    }

    @Test
    void discardsAFailedReadThatIsOlderThanTheRowsRead() {
        baskets.save(aBasketWith(aFirstItem()));
        Basket before = service.basket(BASKET_ID);

        Basket observed = service.applyObservation(BASKET_ID, aCommandOf(aFailedReadReadAt(AN_EARLIER_READ_AT)));

        assertThat(observed).isEqualTo(before);
    }

    @Test
    void leavesATerminalBasketAlone() {
        baskets.save(anArchivedBasketWith(aFirstItem()));

        Basket observed = service.applyObservation(BASKET_ID, aCommandOf(anOfferOnSaleWithStockReadAt(0, A_LATER_READ_AT)));

        assertThat(observed.items()).singleElement()
                .satisfies(item -> assertThat(item.availability()).isEqualTo(Availability.available()));
        assertThat(baskets.saved).containsExactly(BASKET_ID.value());
    }

    @Test
    void neverReservesWhatItReads() {
        FixtureOfferCatalog catalog = aCatalog();
        catalog.sells(OFFER, 1);
        baskets.save(aBasketWith(aFirstItem().withQuantity(10)));

        List<OfferRecord> read = catalog.read(List.of(FIRST_OFFER));
        service.applyObservation(BASKET_ID, aCommandOf(read.get(0)));

        assertThat(catalog.reads()).singleElement().isEqualTo(List.of(FIRST_OFFER));
        assertThat(catalog.read(List.of(FIRST_OFFER)).get(0).stock())
                .isEqualTo(new OfferObservation.Stock.Quantity(1));
    }

    @Test
    void refreshesThePriceWithoutReDecidingAvailability() {
        baskets.save(aBasketWith(anUnavailableItem()));

        Basket observed = service.applyObservation(BASKET_ID,
                aPriceChangeOf(anUnavailableItem().offerId(), A_LATER_READ_AT, LIST_PRICE));

        assertThat(observed.items()).singleElement().satisfies(item -> {
            assertThat(item.availability()).isEqualTo(Availability.removed());
            assertThat(item.lastKnown().currentPrice().amount()).isEqualTo(LIST_PRICE);
            assertThat(item.readAt()).isEqualTo(A_LATER_READ_AT);
        });
        assertThat(observed.revision()).isEqualTo(1);
    }

    @Test
    void discardsAPriceChangeOlderThanTheRowsRead() {
        baskets.save(aBasketWith(aFirstItem()));
        Basket before = service.basket(BASKET_ID);

        Basket observed = service.applyObservation(BASKET_ID, aPriceChangeOf(OFFER, AN_EARLIER_READ_AT, LIST_PRICE));

        assertThat(observed).isEqualTo(before);
        assertThat(baskets.saved).containsExactly(BASKET_ID.value());
    }

    @Test
    void regroupsTheRowUnderTheSellerTheProviderNamed() {
        baskets.save(aBasketWith(aFirstItem()));

        Basket observed = service.applyObservation(BASKET_ID, aCommandOf(anOfferSoldBy(BasketFixture.ANKER)));

        assertThat(observed.items()).singleElement()
                .satisfies(item -> assertThat(item.seller()).isEqualTo(BasketFixture.ANKER));
        assertThat(BasketFixture.shipmentOf(BasketFixture.UGREEN).covers(observed.items().get(0))).isFalse();
    }

    @Test
    void keepsTheTitleTheProviderLeftOut() {
        baskets.save(aBasketWith(aFirstItem()));

        Basket observed = service.applyObservation(BASKET_ID, aCommandOf(withoutTitle()));

        assertThat(observed.items()).singleElement().satisfies(item -> {
            assertThat(item.lastKnown().title()).isEqualTo(aLastKnown().title());
            assertThat(item.lastKnown().currentPrice()).isEqualTo(A_CHARGED_PRICE);
            assertThat(item.lastKnown().discount()).isEqualTo(A_DISCOUNTED_AMOUNT);
        });
    }

    @Test
    void storesANewerReadOfUnchangedContentWithoutBumpingTheRevision() {
        baskets.save(aBasketWith(aFirstItem()));
        service.applyObservation(BASKET_ID, aCommandOf(anOfferOnSaleWithStock(SOME_UNITS)));

        Basket reread = service.applyObservation(BASKET_ID,
                aCommandOf(anOfferOnSaleWithStockReadAt(SOME_UNITS, A_LATER_READ_AT)));

        assertThat(reread.revision()).isEqualTo(1);
        assertThat(reread.items()).singleElement()
                .satisfies(item -> assertThat(item.readAt()).isEqualTo(A_LATER_READ_AT));
        assertThat(baskets.saved).hasSize(3);
    }

    @Test
    void changesTheNextHandoverNotTheOneThatCrossed() {
        baskets.save(aBasketWith(aFirstItem()));
        Handover crossed = service.handOver(BASKET_ID);

        service.applyObservation(BASKET_ID, aPriceChangeOf(OFFER, A_LATER_READ_AT, LIST_PRICE));
        Handover next = service.handOver(BASKET_ID);

        assertThat(crossed.selection().get(0).currentPrice()).isEqualTo(aLastKnown().currentPrice());
        assertThat(next.selection().get(0).currentPrice().amount()).isEqualTo(LIST_PRICE);
        assertThat(next.revision()).isGreaterThan(crossed.revision());
    }

    @Test
    void refusesABasketNobodyHas() {
        BasketId unknown = anotherBasketId();

        assertThatThrownBy(() -> service.applyObservation(unknown, aCommandOf(aFailedRead())))
                .isInstanceOfSatisfying(ShoppingException.class,
                        refused -> assertThat(refused.error()).isEqualTo(ShoppingException.Error.BASKET_NOT_FOUND));
    }

    @Test
    void adoptsARowFromBeforeTheReadInstantWasKept() {
        BasketEditor beforeReadAt = aBasketWith(aRowFromBeforeTheReadInstantWasKept());
        baskets.save(beforeReadAt);

        assertThat(beforeReadAt.toSnapshot().items().get(0).readAt()).isEqualTo(Instant.EPOCH);

        Basket observed = service.applyObservation(BASKET_ID, aCommandOf(anOfferOnSaleWithStock(SOME_UNITS)));

        assertThat(observed.items()).singleElement()
                .satisfies(item -> assertThat(item.readAt()).isEqualTo(READ_AT));
    }
}
