package shop.shopping.basket;

import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import shop.shopping.kernel.Availability;

import static org.assertj.core.api.Assertions.assertThat;
import static shop.shopping.basket.BasketFixture.aFirstItem;
import static shop.shopping.basket.OfferAvailabilityDecision.decide;
import static shop.shopping.basket.OfferAvailabilityFixture.A_LATER_READ_AT;
import static shop.shopping.basket.OfferAvailabilityFixture.A_NEGATIVE_COUNT;
import static shop.shopping.basket.OfferAvailabilityFixture.AN_EARLIER_READ_AT;
import static shop.shopping.basket.OfferAvailabilityFixture.ONE_UNIT;
import static shop.shopping.basket.OfferAvailabilityFixture.SOME_UNITS;
import static shop.shopping.basket.OfferAvailabilityFixture.aFailedRead;
import static shop.shopping.basket.OfferAvailabilityFixture.aFailedReadOf;
import static shop.shopping.basket.OfferAvailabilityFixture.aRemovedOffer;
import static shop.shopping.basket.OfferAvailabilityFixture.aRemovedOfferWithStock;
import static shop.shopping.basket.OfferAvailabilityFixture.anObservationOf;
import static shop.shopping.basket.OfferAvailabilityFixture.anOfferOnSaleWhoseStockWasNotRead;
import static shop.shopping.basket.OfferAvailabilityFixture.anOfferOnSaleWithStock;
import static shop.shopping.basket.OfferAvailabilityFixture.anOfferOnSaleWithoutStock;
import static shop.shopping.basket.OfferAvailabilityFixture.everyStock;

/**
 * Element 06's interface scenarios I1–I7, one test per row, each pinning the three components of the outcome: the status, the
 * reason behind it and whether the read is stale.
 *
 * <p>Three further tests pin what no row isolates: the blocking set of D33 — {@code outOfStock} and {@code unavailable} block
 * the handover, {@code stale} on its own never does — the totality the policy claims, every combination of offer state, stock
 * answer and read instant landing on one of the five named outcomes, and the two edges of the stock answer: the last unit left
 * is still available and a count no warehouse sends leaves none left.
 */
class OfferAvailabilityDecisionTest {

    @Test
    void i1AnOfferOnSaleWithStockIsAvailable() {
        Availability decided = decide(anOfferOnSaleWithStock(SOME_UNITS));

        assertThat(decided.status()).isEqualTo(Availability.Status.available);
        assertThat(decided.reason()).isEqualTo(Availability.Reason.stillOnSale);
        assertThat(decided.stale()).isFalse();
    }

    @Test
    void i2AnOfferOnSaleWithNoStockIsOutOfStock() {
        Availability decided = decide(anOfferOnSaleWithoutStock());

        assertThat(decided.status()).isEqualTo(Availability.Status.outOfStock);
        assertThat(decided.reason()).isEqualTo(Availability.Reason.noStock);
        assertThat(decided.stale()).isFalse();
    }

    @Test
    void i3ARemovedOfferIsUnavailable() {
        Availability decided = decide(aRemovedOffer());

        assertThat(decided.status()).isEqualTo(Availability.Status.unavailable);
        assertThat(decided.reason()).isEqualTo(Availability.Reason.removedFromOffer);
        assertThat(decided.stale()).isFalse();
    }

    @Test
    void i4AFailedReadAfterAPreviousAvailableDoesNotKeepThatAvailable() {
        Availability decided = decide(aFailedReadOf(aFirstItem()));

        assertThat(decided.status()).isEqualTo(Availability.Status.unavailable);
        assertThat(decided.reason()).isEqualTo(Availability.Reason.notRead);
        assertThat(decided.stale()).isTrue();
    }

    @Test
    void i5AnOfferOnSaleWhoseStockWasNotReadIsAvailableAndStale() {
        Availability decided = decide(anOfferOnSaleWhoseStockWasNotRead());

        assertThat(decided.status()).isEqualTo(Availability.Status.available);
        assertThat(decided.reason()).isEqualTo(Availability.Reason.stillOnSale);
        assertThat(decided.stale()).isTrue();
    }

    @Test
    void i6AFailedFirstReadIsUnavailableAndStaleLikeAnyOtherFailedRead() {
        Availability decided = decide(aFailedRead());

        assertThat(decided.status()).isEqualTo(Availability.Status.unavailable);
        assertThat(decided.reason()).isEqualTo(Availability.Reason.notRead);
        assertThat(decided.stale()).isTrue();
    }

    @Test
    void i7AFirstReadOfARemovedOfferIsUnavailableEvenThoughTheWarehouseHasStock() {
        Availability decided = decide(aRemovedOfferWithStock(SOME_UNITS));

        assertThat(decided.status()).isEqualTo(Availability.Status.unavailable);
        assertThat(decided.reason()).isEqualTo(Availability.Reason.removedFromOffer);
        assertThat(decided.stale()).isFalse();
    }

    @ParameterizedTest
    @MethodSource("everyDecidedOutcome")
    void onlyTheTwoNonAvailableStatusesBlockHandover(OfferObservation observation, boolean blocks) {
        assertThat(decide(observation).blocksHandover()).isEqualTo(blocks);
    }

    @ParameterizedTest
    @MethodSource("shop.shopping.basket.OfferAvailabilityFixture#everyObservation")
    void everyObservationIsDecidedIntoOneOfTheFiveNamedOutcomes(OfferObservation observation) {
        Availability decided = decide(observation);

        assertThat(decided).isIn(Availability.available(), Availability.availableWithoutStockRead(),
                Availability.outOfStock(), Availability.removed(), Availability.unreadable());
        assertThat(decide(observation)).isEqualTo(decided);
    }

    @Test
    void theLastUnitLeftIsStillAvailable() {
        Availability decided = decide(anOfferOnSaleWithStock(ONE_UNIT));

        assertThat(decided.status()).isEqualTo(Availability.Status.available);
        assertThat(decided.reason()).isEqualTo(Availability.Reason.stillOnSale);
        assertThat(decided.stale()).isFalse();
    }

    @Test
    void aCountBelowZeroLeavesNoStockLeft() {
        Availability decided = decide(anOfferOnSaleWithStock(A_NEGATIVE_COUNT));

        assertThat(decided.status()).isEqualTo(Availability.Status.outOfStock);
        assertThat(decided.reason()).isEqualTo(Availability.Reason.noStock);
        assertThat(decided.stale()).isFalse();
    }

    @ParameterizedTest
    @MethodSource("everyOfferStateAndStock")
    void theInstantOfTheReadNeverChangesTheOutcome(OfferObservation.OfferState offerState, OfferObservation.Stock stock) {
        Availability readLongAgo = decide(anObservationOf(offerState, stock, AN_EARLIER_READ_AT));
        Availability readLater = decide(anObservationOf(offerState, stock, A_LATER_READ_AT));

        assertThat(readLongAgo).isEqualTo(readLater);
    }

    static Stream<Arguments> everyOfferStateAndStock() {
        return Stream.of(OfferObservation.OfferState.values())
                .flatMap(offerState -> everyStock().map(stock -> Arguments.of(offerState, stock)));
    }

    static Stream<Arguments> everyDecidedOutcome() {
        return Stream.of(
                Arguments.of(anOfferOnSaleWithStock(SOME_UNITS), false),
                Arguments.of(anOfferOnSaleWhoseStockWasNotRead(), false),
                Arguments.of(anOfferOnSaleWithoutStock(), true),
                Arguments.of(aRemovedOffer(), true),
                Arguments.of(aFailedRead(), true));
    }
}
