package shop.shopping.basket;

import org.junit.jupiter.api.Test;

import java.util.List;

import shop.shopping.kernel.Availability;
import shop.shopping.kernel.Handover;
import shop.shopping.kernel.ShoppingException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static shop.shopping.basket.BasketFixture.ANKER;
import static shop.shopping.basket.BasketFixture.BASKET_ID;
import static shop.shopping.basket.BasketFixture.FIRST_OFFER;
import static shop.shopping.basket.BasketFixture.NOW;
import static shop.shopping.basket.BasketFixture.SECOND_OFFER;
import static shop.shopping.basket.BasketFixture.THIRD_OFFER;
import static shop.shopping.basket.BasketFixture.UGREEN;
import static shop.shopping.basket.BasketFixture.aBasketWith;
import static shop.shopping.basket.BasketFixture.aFirstItem;
import static shop.shopping.basket.BasketFixture.aSecondItem;
import static shop.shopping.basket.BasketFixture.aSnapshotOf;
import static shop.shopping.basket.BasketFixture.aThirdItemOfAnotherSeller;
import static shop.shopping.basket.BasketFixture.anArchivedBasketWith;
import static shop.shopping.basket.BasketFixture.anEmptyBasket;
import static shop.shopping.basket.BasketFixture.anItem;
import static shop.shopping.basket.BasketFixture.anItemInAnotherCurrency;
import static shop.shopping.basket.BasketFixture.anItemReadAsStale;
import static shop.shopping.basket.BasketFixture.anOutOfStockItem;
import static shop.shopping.basket.BasketFixture.anUnavailableItem;
import static shop.shopping.basket.BasketFixture.selectionOf;
import static shop.shopping.basket.BasketFixture.shipmentOf;
import static shop.shopping.basket.BasketFixture.wholeBasket;
import static shop.shopping.kernel.HandoverFixture.aRefusal;
import static shop.shopping.kernel.HandoverFixture.theTotalsOfOneItem;
import static shop.shopping.kernel.HandoverFixture.totalsOf;

/**
 * Element 02 rule by rule, through the snapshot the aggregate exposes: what a repeated add does (RULE-4, D19), what a
 * quantity may be (RULE-3) and on which row it may change (D19), what survives an erased offer (RULE-5, RULE-8), what
 * blocking means (RULE-6, D33), what a selection is and is not (RULE-9, RULE-10) and what crosses on handover (element 09
 * HAND-1…HAND-6).
 */
class BasketEditorTest {

    @Test
    void addsAnOfferAsASelectedRow() {
        BasketEditor basket = anEmptyBasket();

        basket.addToBasket(aFirstItem());

        assertThat(aSnapshotOf(basket).items()).singleElement().satisfies(item -> {
            assertThat(item.offerId()).isEqualTo(FIRST_OFFER);
            assertThat(item.quantity()).isEqualTo(1);
            assertThat(item.selected()).isTrue();
            assertThat(item.seller()).isEqualTo(UGREEN);
        });
    }

    @Test
    void mergesTheQuantityOfARepeatedAdd() {
        BasketEditor basket = aBasketWith(aFirstItem());

        basket.addToBasket(anItem(FIRST_OFFER, UGREEN, 2, Availability.available()));

        assertThat(aSnapshotOf(basket).items()).singleElement()
                .satisfies(item -> assertThat(item.quantity()).isEqualTo(3));
    }

    @Test
    void refusesAnOfferPricedInAnotherCurrency() {
        BasketEditor basket = anEmptyBasket();

        assertThatThrownBy(() -> basket.addToBasket(anItemInAnotherCurrency()))
                .isInstanceOfSatisfying(ShoppingException.class, refused -> assertThat(refused.error())
                        .isEqualTo(ShoppingException.Error.CURRENCY_MISMATCH));
    }

    @Test
    void refusesAQuantityBelowOne() {
        BasketEditor basket = aBasketWith(aFirstItem());

        assertThatThrownBy(() -> basket.changeQuantity(FIRST_OFFER, 0))
                .isInstanceOfSatisfying(ShoppingException.class, refused -> assertThat(refused.error())
                        .isEqualTo(ShoppingException.Error.QUANTITY_NOT_POSITIVE));
    }

    @Test
    void refusesAQuantityChangeOfAnOfferTheBasketDoesNotHold() {
        BasketEditor basket = aBasketWith(aFirstItem());

        assertThatThrownBy(() -> basket.changeQuantity(SECOND_OFFER, 2))
                .isInstanceOfSatisfying(ShoppingException.class, refused -> assertThat(refused.error())
                        .isEqualTo(ShoppingException.Error.OFFER_NOT_FOUND));
    }

    @Test
    void refusesAQuantityChangeOnAnUnavailableRow() {
        BasketEditor basket = aBasketWith(anUnavailableItem());

        assertThatThrownBy(() -> basket.changeQuantity(BasketFixture.THIRD_OFFER, 2))
                .isInstanceOfSatisfying(ShoppingException.class, refused -> assertThat(refused.error())
                        .isEqualTo(ShoppingException.Error.OFFER_UNAVAILABLE));
    }

    @Test
    void allowsAQuantityChangeOnAnOutOfStockRow() {
        BasketEditor basket = aBasketWith(anOutOfStockItem());

        basket.changeQuantity(SECOND_OFFER, 4);

        assertThat(aSnapshotOf(basket).items()).singleElement()
                .satisfies(item -> assertThat(item.quantity()).isEqualTo(4));
    }

    @Test
    void keepsAnUnavailableRowAndItsLastKnownSnapshotThroughARepeatedAdd() {
        BasketEditor basket = aBasketWith(anUnavailableItem());

        basket.addToBasket(anItem(THIRD_OFFER, ANKER, 2, Availability.available()));

        assertThat(aSnapshotOf(basket).items()).singleElement().satisfies(item -> {
            assertThat(item.offerId()).isEqualTo(THIRD_OFFER);
            assertThat(item.availability().status()).isEqualTo(Availability.Status.unavailable);
            assertThat(item.quantity()).isEqualTo(3);
            assertThat(item.lastKnown().title()).startsWith("UGREEN Nexode");
            assertThat(item.lastKnown().picture()).isEqualTo("img-1001");
            assertThat(item.lastKnown().currentPrice().amount()).isEqualTo(99890);
        });
    }

    @Test
    void bumpsTheRevisionOnEveryChange() {
        BasketEditor basket = aBasketWith(aFirstItem());

        basket.addToBasket(aSecondItem());
        basket.changeQuantity(FIRST_OFFER, 2);
        basket.select(wholeBasket(), false);
        basket.removeItem(SECOND_OFFER);

        assertThat(aSnapshotOf(basket).revision()).isEqualTo(4);
    }

    @Test
    void removesOneRowAndLeavesAnEmptyActiveBasket() {
        BasketEditor basket = aBasketWith(aFirstItem(), aSecondItem());

        basket.removeItem(FIRST_OFFER);

        Basket snapshot = aSnapshotOf(basket);
        assertThat(snapshot.items()).extracting(BasketItem::offerId).containsExactly(SECOND_OFFER);
        assertThat(snapshot.state()).isEqualTo(BasketState.active);
    }

    @Test
    void removesOnlyTheSelectedRows() {
        BasketEditor basket = aBasketWith(aFirstItem(), aSecondItem());
        basket.select(selectionOf(SECOND_OFFER), false);

        basket.removeSelectedItems();

        assertThat(aSnapshotOf(basket).items()).extracting(BasketItem::offerId).containsExactly(SECOND_OFFER);
    }

    @Test
    void removesEverythingAndTheBasketStaysActive() {
        BasketEditor basket = aBasketWith(aFirstItem(), aSecondItem(), aThirdItemOfAnotherSeller());

        basket.removeEverything();

        assertThat(aSnapshotOf(basket).items()).isEmpty();
        assertThat(aSnapshotOf(basket).state()).isEqualTo(BasketState.active);
        assertThat(basket.events).filteredOn(DomainEvent.RemovedFromBasket.class::isInstance).hasSize(3);
    }

    @Test
    void removesNothingWhenNothingIsSelected() {
        BasketEditor basket = aBasketWith(aFirstItem());
        basket.select(wholeBasket(), false);
        long revision = aSnapshotOf(basket).revision();
        basket.events.clear();

        basket.removeSelectedItems();

        assertThat(aSnapshotOf(basket).items()).hasSize(1);
        assertThat(aSnapshotOf(basket).revision()).isEqualTo(revision);
        assertThat(basket.events).isEmpty();
    }

    @Test
    void selectsEveryRowOfOneShipmentWithoutEmittingAFact() {
        BasketEditor basket = aBasketWith(aFirstItem(), aSecondItem(), aThirdItemOfAnotherSeller());

        basket.select(shipmentOf(ANKER), false);

        assertThat(aSnapshotOf(basket).items())
                .filteredOn(item -> !item.selected())
                .extracting(BasketItem::seller).containsExactly(ANKER);
        assertThat(basket.events).isEmpty();
    }

    @Test
    void refusesASelectionOfAnOfferTheBasketDoesNotHold() {
        BasketEditor basket = aBasketWith(aFirstItem());

        assertThatThrownBy(() -> basket.select(selectionOf(SECOND_OFFER), true))
                .isInstanceOfSatisfying(ShoppingException.class, refused -> {
                    assertThat(refused.error()).isEqualTo(ShoppingException.Error.OFFER_NOT_FOUND);
                    assertThat(refused.details()).containsExactly(SECOND_OFFER);
                });
    }

    @Test
    void deselectsEverythingAndStillIsAValidBasket() {
        BasketEditor basket = aBasketWith(aFirstItem());
        basket.select(wholeBasket(), false);

        assertThatThrownBy(() -> handedOver(basket))
                .isInstanceOfSatisfying(ShoppingException.class, refused -> assertThat(refused.error())
                        .isEqualTo(ShoppingException.Error.NOTHING_SELECTED));
    }

    @Test
    void refusesTheBlockedHandoverUntilTheBlockedRowIsDeselected() {
        BasketEditor basket = aBasketWith(aFirstItem(), anOutOfStockItem());
        assertThatThrownBy(() -> handedOver(basket))
                .isInstanceOfSatisfying(ShoppingException.class, refused -> {
                    assertThat(refused.error()).isEqualTo(ShoppingException.Error.BLOCKED_BY_ITEMS);
                    assertThat(refused.details()).containsExactly(BASKET_ID.value(),
                            List.of(aRefusal(SECOND_OFFER, Availability.Status.outOfStock, false)));
                });
        assertThat(aSnapshotOf(basket).lastHandover()).isNull();

        basket.select(selectionOf(SECOND_OFFER), false);
        HandoverDraft draft = handedOver(basket);

        assertThat(draft.selection()).extracting(Handover.Line::offerId).containsExactly(FIRST_OFFER);
        assertThat(aSnapshotOf(basket).lastHandover().revision()).isEqualTo(draft.identity().revision());
    }

    @Test
    void keepsTheBasketEditableAfterTheHandover() {
        BasketEditor basket = aBasketWith(aFirstItem());
        handedOver(basket);

        basket.changeQuantity(FIRST_OFFER, 2);

        assertThat(aSnapshotOf(basket).state()).isEqualTo(BasketState.active);
        assertThat(aSnapshotOf(basket).items()).singleElement()
                .satisfies(item -> assertThat(item.quantity()).isEqualTo(2));
    }

    @Test
    void handsOverTheSameIdentityWhileTheContentIsUnchanged() {
        BasketEditor basket = aBasketWith(aFirstItem());

        HandoverDraft first = handedOver(basket);
        HandoverDraft repeated = handedOver(basket);

        assertThat(repeated.identity()).isEqualTo(first.identity());
        assertThat(repeated.selection()).isEqualTo(first.selection());
        assertThat(first.repeated()).isFalse();
        assertThat(repeated.repeated()).isTrue();
        assertThat(basket.events).filteredOn(DomainEvent.GoToCheckout.class::isInstance).hasSize(1);
    }

    @Test
    void handsOverANewIdentityAfterTheContentChanged() {
        BasketEditor basket = aBasketWith(aFirstItem());
        HandoverDraft first = handedOver(basket);
        String firstDigest = aSnapshotOf(basket).lastHandover().contentDigest();

        basket.changeQuantity(FIRST_OFFER, 2);
        HandoverDraft second = handedOver(basket);

        assertThat(second.repeated()).isFalse();
        assertThat(second.identity().revision()).isEqualTo(first.identity().revision() + 2);
        assertThat(aSnapshotOf(basket).lastHandover().contentDigest()).isNotEqualTo(firstDigest);
    }

    @Test
    void refusesAHandoverBlockedByAnUnavailableRow() {
        BasketEditor basket = aBasketWith(aFirstItem(), anUnavailableItem());

        assertThatThrownBy(() -> handedOver(basket))
                .isInstanceOfSatisfying(ShoppingException.class, refused -> {
                    assertThat(refused.error()).isEqualTo(ShoppingException.Error.BLOCKED_BY_ITEMS);
                    assertThat(refused.details()).containsExactly(BASKET_ID.value(),
                            List.of(aRefusal(THIRD_OFFER, Availability.Status.unavailable, false)));
                });
    }

    @Test
    void handsOverARowWhoseOfferAnsweredButWhoseStockDidNot() {
        BasketEditor basket = aBasketWith(anItemReadAsStale());

        HandoverDraft draft = handedOver(basket);

        assertThat(draft.selection()).singleElement().satisfies(line -> {
            assertThat(line.availability().status()).isEqualTo(Availability.Status.available);
            assertThat(line.availability().stale()).isTrue();
        });
    }

    @Test
    void refusesAHandoverOfAnArchivedBasket() {
        BasketEditor basket = anArchivedBasketWith(aFirstItem());

        assertThatThrownBy(() -> handedOver(basket))
                .isInstanceOfSatisfying(ShoppingException.class, refused -> assertThat(refused.error())
                        .isEqualTo(ShoppingException.Error.BASKET_INACTIVE));
    }

    @Test
    void handsOverANewIdentityWhenTheTotalsMoved() {
        BasketEditor basket = aBasketWith(aFirstItem());
        HandoverDraft first = handedOver(basket);

        HandoverDraft repriced = basket.handOver(NOW, totalsOf(99890, 0, 12_340));

        assertThat(repriced.repeated()).isFalse();
        assertThat(repriced.identity().revision()).isEqualTo(first.identity().revision() + 1);
        assertThat(repriced.identity().contentDigest()).isNotEqualTo(first.identity().contentDigest());
    }

    private static HandoverDraft handedOver(BasketEditor basket) {
        return basket.handOver(NOW, theTotalsOfOneItem());
    }
}
