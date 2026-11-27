package shop.shopping.basket;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.List;

import shop.shopping.kernel.Availability;
import shop.shopping.kernel.Money;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static shop.shopping.basket.OfferCatalogFixture.ANOTHER_OFFER;
import static shop.shopping.basket.OfferCatalogFixture.A_NEGATIVE_COUNT;
import static shop.shopping.basket.OfferCatalogFixture.CURRENT_PRICE;
import static shop.shopping.basket.OfferCatalogFixture.NO_UNITS;
import static shop.shopping.basket.OfferCatalogFixture.OFFER;
import static shop.shopping.basket.OfferCatalogFixture.READ_AT;
import static shop.shopping.basket.OfferCatalogFixture.SELLER;
import static shop.shopping.basket.OfferCatalogFixture.SOME_UNITS;
import static shop.shopping.basket.OfferCatalogFixture.aCatalog;
import static shop.shopping.basket.OfferCatalogFixture.aDescription;
import static shop.shopping.basket.OfferSeedFixture.aDescribedRemoval;
import static shop.shopping.basket.OfferSeedFixture.aSeededCatalog;
import static shop.shopping.basket.OfferSeedFixture.anOnSaleSeed;
import static shop.shopping.basket.OfferSeedFixture.anOnSaleSeedThatFaultsAfter;
import static shop.shopping.basket.OfferSeedFixture.anUnreadableSeed;
import static shop.shopping.basket.OfferCatalogFixture.aDescriptionPricedIn;
import static shop.shopping.basket.OfferCatalogFixture.aSnapshotPricedInTwoCurrencies;
import static shop.shopping.basket.OfferCatalogFixture.aWithdrawnOffer;
import static shop.shopping.basket.OfferCatalogFixture.anUnreadOfferDescribing;
import static shop.shopping.basket.OfferCatalogFixture.anOfferIdPerRow;
import static shop.shopping.basket.OfferCatalogFixture.anOfferOf;
import static shop.shopping.basket.OfferCatalogFixture.withoutASeller;
import static shop.shopping.basket.OfferCatalogFixture.withoutTitle;

/**
 * Element 08's port and its fixture adapter: one batched call, one record per offered id, and every provider outcome mapped to
 * the answer the basket needs instead of to an exception (§Port, §Failure mapping).
 *
 * <p>The negative rows are the point of this adapter — it is the fault injector DoD18 and DoD19 need — so each of the four
 * ways a read can fail the basket is a row here, together with the two malformed answers that must <em>not</em> be repaired:
 * a stock count below zero (D56) and a call the provider refused as mis-addressed.
 */
class OfferCatalogTest {

    final FixtureOfferCatalog catalog = aCatalog();

    @Test
    void readsABasketFullOfOffersInOneCall() {
        List<String> offerIds = anOfferIdPerRow();
        offerIds.forEach(offerId -> catalog.sells(offerId, SOME_UNITS));

        List<OfferRecord> read = catalog.read(offerIds);

        assertThat(read).extracting(OfferRecord::offerId).containsExactlyElementsOf(offerIds);
        assertThat(catalog.reads()).singleElement().isEqualTo(offerIds);
    }

    @Test
    void asksTheProviderNothingWhenThereIsNothingToRead() {
        assertThat(catalog.read(List.of())).isEmpty();
        assertThat(catalog.reads()).isEmpty();
    }

    @Test
    void answersWhatTheProviderSaidAboutTheOffer() {
        catalog.sells(OFFER, SOME_UNITS);

        OfferRecord read = catalog.read(List.of(OFFER)).get(0);

        assertThat(read.offerId()).isEqualTo(OFFER);
        assertThat(read.offerState()).isEqualTo(OfferObservation.OfferState.onSale);
        assertThat(read.stock()).isEqualTo(new OfferObservation.Stock.Quantity(SOME_UNITS));
        assertThat(read.readAt()).isEqualTo(READ_AT);
        assertThat(read.description()).satisfies(described -> {
            assertThat(described.seller()).isEqualTo(FixtureOfferCatalog.SELLER);
            assertThat(described.currentPrice().amount()).isEqualTo(CURRENT_PRICE);
        });
    }

    @Test
    void answersAnIdTheProviderLeftOutOfItsTwoHundredAsRemoved() {
        catalog.sells(OFFER, SOME_UNITS);

        List<OfferRecord> read = catalog.read(List.of(OFFER, ANOTHER_OFFER));

        assertThat(read).extracting(OfferRecord::offerId).containsExactly(OFFER, ANOTHER_OFFER);
        assertThat(read).extracting(OfferRecord::offerState)
                .containsExactly(OfferObservation.OfferState.onSale, OfferObservation.OfferState.removed);
        assertThat(read.get(1).description()).isNull();
    }

    @Test
    void answersAWithdrawnOfferAsRemovedWithNothingToDescribe() {
        catalog.removed(OFFER);

        OfferRecord read = catalog.read(List.of(OFFER)).get(0);

        assertThat(read.offerState()).isEqualTo(OfferObservation.OfferState.removed);
        assertThat(read.description()).isNull();
    }

    @ParameterizedTest
    @EnumSource(value = FixtureOfferCatalog.Failure.class,
            names = {"unauthorized", "forbidden", "timeout", "serverError"})
    void failsTheWholeReadWhenTheProviderDoesNotAnswer(FixtureOfferCatalog.Failure fault) {
        catalog.sells(OFFER, SOME_UNITS);
        catalog.failingWith(fault);

        List<OfferRecord> read = catalog.read(List.of(OFFER, ANOTHER_OFFER));

        assertThat(read).extracting(OfferRecord::offerState)
                .containsOnly(OfferObservation.OfferState.notRead);
        assertThat(read).extracting(OfferRecord::stock).containsOnly(OfferObservation.Stock.NotRead.notRead);
        assertThat(catalog.reads()).singleElement().isEqualTo(List.of(OFFER, ANOTHER_OFFER));
    }

    @Test
    void answersNothingWhenTheProviderRefusedTheCallItself() {
        catalog.sells(OFFER, SOME_UNITS);
        catalog.failingWith(FixtureOfferCatalog.Failure.malformedRequest);

        assertThat(catalog.read(List.of(OFFER))).isEmpty();
    }

    @Test
    void failsTheWholeReadForAnOfferTheSeedDeclaresUnreadable() {
        FixtureOfferCatalog seeded = aSeededCatalog(anOnSaleSeed(OFFER, SOME_UNITS),
                anUnreadableSeed(ANOTHER_OFFER));

        List<OfferRecord> read = seeded.read(List.of(OFFER, ANOTHER_OFFER));

        assertThat(read).extracting(OfferRecord::offerState)
                .containsOnly(OfferObservation.OfferState.notRead);
        assertThat(read).extracting(OfferRecord::stock).containsOnly(OfferObservation.Stock.NotRead.notRead);
        assertThat(seeded.read(List.of(OFFER))).extracting(OfferRecord::offerState)
                .containsOnly(OfferObservation.OfferState.onSale);
    }

    @Test
    void faultsOnlyFromTheReadTheSeedNames() {
        FixtureOfferCatalog seeded = aSeededCatalog(anOnSaleSeedThatFaultsAfter(OFFER, 2));

        assertThat(seeded.read(List.of(OFFER))).extracting(OfferRecord::offerState)
                .containsOnly(OfferObservation.OfferState.onSale);
        assertThat(seeded.read(List.of(OFFER))).extracting(OfferRecord::offerState)
                .containsOnly(OfferObservation.OfferState.onSale);
        assertThat(seeded.read(List.of(OFFER))).extracting(OfferRecord::offerState)
                .containsOnly(OfferObservation.OfferState.notRead);
    }

    @Test
    void keepsAStockTheWarehouseNeverAnswered() {
        catalog.sellsWithoutStockRead(OFFER);

        OfferRecord read = catalog.read(List.of(OFFER)).get(0);

        assertThat(read.offerState()).isEqualTo(OfferObservation.OfferState.onSale);
        assertThat(read.stock()).isEqualTo(OfferObservation.Stock.NotRead.notRead);
    }

    @Test
    void passesACountBelowZeroThroughUntouched() {
        catalog.sells(OFFER, A_NEGATIVE_COUNT);

        assertThat(catalog.read(List.of(OFFER)).get(0).stock())
                .isEqualTo(new OfferObservation.Stock.Quantity(A_NEGATIVE_COUNT));
    }

    @Test
    void keepsTheRowsOfTwoUnknownSellersInTwoShipments() {
        OfferRecord first = withoutASeller(OFFER);
        OfferRecord second = withoutASeller(ANOTHER_OFFER);

        assertThat(first.sellerOrPlaceholder()).isEqualTo("unknown seller " + OFFER);
        assertThat(second.sellerOrPlaceholder()).isEqualTo("unknown seller " + ANOTHER_OFFER);
        BasketSelection shipment = aShipmentOf(first.sellerOrPlaceholder());
        assertThat(shipment.covers(aRowFor(first))).isTrue();
        assertThat(shipment.covers(aRowFor(second))).isFalse();
    }

    @Test
    void keepsTheSellersOwnNameWhenTheProviderNamedOne() {
        assertThat(withoutASeller(OFFER).sellerOrPlaceholder()).isNotEqualTo(SELLER);

        assertThat(anOfferOf(OFFER, OfferObservation.OfferState.onSale, READ_AT).sellerOrPlaceholder())
                .isEqualTo(SELLER);
    }

    @Test
    void refusesAReadThatFailedCarryingAnOfferItNeverRead() {
        assertThatThrownBy(() -> anUnreadOfferDescribing(aDescription()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("describes nothing");
    }

    @Test
    void allowsAnAnswerThatDescribesNoContent() {
        OfferRecord withdrawn = aWithdrawnOffer();

        assertThat(withdrawn.offerState()).isEqualTo(OfferObservation.OfferState.removed);
        assertThat(withdrawn.description()).isNull();
        assertThat(withdrawn.sellerOrPlaceholder()).isEqualTo("unknown seller " + OFFER);
    }

    @Test
    void allowsAnAnswerThatNamesOnlySomeOfTheFields() {
        OfferRecord partial = withoutTitle();

        assertThat(partial.description().title()).isNull();
        assertThat(partial.description().currentPrice().amount()).isEqualTo(CURRENT_PRICE);
    }

    @Test
    void refusesAnOfferPricedInTwoCurrencies() {
        assertThatThrownBy(() -> aDescriptionPricedIn(new Money(CURRENT_PRICE, "EUR")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("one currency");
    }

    @Test
    void refusesASnapshotWhoseTwoPricesAreInTwoCurrencies() {
        assertThatThrownBy(() -> OfferObservationCommand.refreshed(OFFER, READ_AT, aSnapshotPricedInTwoCurrencies()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("one currency");
    }

    @Test
    void answersOneSeedQueueEntryPerReadAndRepeatsTheLast() {
        FixtureOfferCatalog seeded = aSeededCatalog(anOnSaleSeed(OFFER, SOME_UNITS, NO_UNITS));

        assertThat(seeded.read(List.of(OFFER))).extracting(OfferRecord::stock)
                .containsExactly(new OfferObservation.Stock.Quantity(SOME_UNITS));
        assertThat(seeded.read(List.of(OFFER))).extracting(OfferRecord::stock)
                .containsExactly(new OfferObservation.Stock.Quantity(NO_UNITS));
        assertThat(seeded.read(List.of(OFFER))).extracting(OfferRecord::stock)
                .containsExactly(new OfferObservation.Stock.Quantity(NO_UNITS));
    }

    @Test
    void startsTheSeedQueueOverWhenThereIsNothingToRead() {
        FixtureOfferCatalog seeded = aSeededCatalog(anOnSaleSeed(OFFER, SOME_UNITS, NO_UNITS));
        seeded.read(List.of(OFFER));

        assertThat(seeded.read(List.of())).isEmpty();

        assertThat(seeded.read(List.of(OFFER))).extracting(OfferRecord::stock)
                .containsExactly(new OfferObservation.Stock.Quantity(SOME_UNITS));
    }

    @Test
    void answersADescribedRemovalWithThePriceTheProviderStillNames() {
        FixtureOfferCatalog seeded = aSeededCatalog(aDescribedRemoval(OFFER));

        OfferRecord read = seeded.read(List.of(OFFER)).get(0);

        assertThat(read.offerState()).isEqualTo(OfferObservation.OfferState.removed);
        assertThat(read.description()).satisfies(described ->
                assertThat(described.currentPrice().amount()).isEqualTo(CURRENT_PRICE));
    }

    @Test
    void refusesASeedThatNamesOneOfferTwice() {
        FixtureOfferCatalog.Offering offering = anOnSaleSeed(OFFER, SOME_UNITS);

        assertThatThrownBy(() -> aSeededCatalog(offering, offering))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("twice");
    }

    private static BasketSelection aShipmentOf(String seller) {
        return BasketFixture.shipmentOf(seller);
    }

    private static BasketItem aRowFor(OfferRecord record) {
        return BasketFixture.anItem(record.offerId(), record.sellerOrPlaceholder(), 1, Availability.available());
    }
}
