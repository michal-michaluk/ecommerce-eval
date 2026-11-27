package shop.shopping.basket;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import shop.shopping.JsonAssert;
import shop.shopping.kernel.Availability;
import shop.shopping.kernel.Handover;
import shop.shopping.kernel.Money;
import shop.shopping.kernel.ShoppingException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static shop.shopping.basket.BasketDeliveryEstimateFixture.FROM;
import static shop.shopping.basket.BasketDeliveryEstimateFixture.LATE_EVENING;
import static shop.shopping.basket.BasketDeliveryEstimateFixture.aFixedClockAt;
import static shop.shopping.basket.BasketDeliveryEstimateFixture.aRequestOf;
import static shop.shopping.basket.BasketDeliveryEstimateFixture.anEstimate;
import static shop.shopping.basket.BasketDeliveryEstimateFixture.TO;
import static shop.shopping.basket.BasketDeliveryEstimateFixture.anEstimatedRange;
import static shop.shopping.basket.BasketFixture.BASKET_ID;
import static shop.shopping.basket.BasketFixture.CUSTOMER_ID;
import static shop.shopping.basket.BasketFixture.EUR;
import static shop.shopping.basket.BasketFixture.FIRST_OFFER;
import static shop.shopping.basket.BasketFixture.NOW;
import static shop.shopping.basket.BasketFixture.PLN;
import static shop.shopping.basket.BasketFixture.SECOND_OFFER;
import static shop.shopping.basket.BasketFixture.THIRD_OFFER;
import static shop.shopping.basket.BasketFixture.aBasketWith;
import static shop.shopping.basket.BasketFixture.aFirstItem;
import static shop.shopping.basket.BasketFixture.aLastKnown;
import static shop.shopping.basket.BasketFixture.aSecondItem;
import static shop.shopping.basket.BasketFixture.aServiceAt;
import static shop.shopping.basket.BasketFixture.aServiceCharging;
import static shop.shopping.basket.BasketFixture.aServiceOf;
import static shop.shopping.basket.BasketFixture.aServiceWith;
import static shop.shopping.basket.BasketFixture.aThirdItemOfAnotherSeller;
import static shop.shopping.basket.BasketFixture.anArchivedBasketWith;
import static shop.shopping.basket.BasketFixture.anItemPricedIn;
import static shop.shopping.basket.BasketFixture.anItemReadAsStale;
import static shop.shopping.basket.BasketFixture.anOutOfStockItem;
import static shop.shopping.basket.BasketFixture.selectionOf;
import static shop.shopping.kernel.HandoverFixture.aRefusal;
import static shop.shopping.kernel.HandoverFixture.theTotalsOfOneItem;
import static shop.shopping.kernel.HandoverFixture.totalsIn;
import static shop.shopping.kernel.HandoverFixture.totalsOf;

/**
 * Element 09 rule by rule through the primary port: only handable rows cross and a refusal leaves the basket untouched (HAND-1),
 * unselected rows are absent rather than zero (HAND-2), the basket creates no order (HAND-3), unchanged content is the same
 * handover and is not sent twice (HAND-4), the prices cross as the basket computed them (HAND-5), and the basket is never
 * cleared or locked (HAND-6).
 *
 * <p>The answer asserted here is the shape of record — steps 18–19 of the contract — so the selection is the offer ids, the
 * totals are element 04's four amounts, and neither {@code checkoutId} nor {@code deliveryEstimate} is published.
 */
class BasketHandoverTest {

    final FakeBasketRepository baskets = new FakeBasketRepository();
    final FakeCheckoutHandovers checkout = new FakeCheckoutHandovers();
    final BasketService service = aServiceWith(baskets, checkout);

    @Test
    void handsOverTheSelectionAndAnswersTheShapeOfRecord() {
        BasketId basketId = service.basketOf(CUSTOMER_ID).basketId();
        service.addToBasket(basketId, aFirstItem());

        HandoverAnswer answer = service.handOver(basketId, Channel.standard);

        assertThat(answer.handoverId()).isEqualTo(basketId.value());
        assertThat(answer.basketId()).isEqualTo(basketId.value());
        assertThat(answer.customerId()).isEqualTo(CUSTOMER_ID.value());
        assertThat(answer.currency()).isEqualTo(PLN);
        assertThat(answer.createdAt()).isEqualTo(NOW);
        assertThat(answer.selection()).containsExactly(FIRST_OFFER);
        assertThat(answer.revision()).isEqualTo(2);
        assertThat(answer.totals()).isEqualTo(theTotalsOfOneItem());
    }

    @Test
    void handsOverOnlyTheSelectedRows() {
        BasketId basketId = service.basketOf(CUSTOMER_ID).basketId();
        service.addToBasket(basketId, aFirstItem());
        service.addToBasket(basketId, aThirdItemOfAnotherSeller());
        service.select(basketId, selectionOf(THIRD_OFFER), false);

        HandoverAnswer answer = service.handOver(basketId, Channel.standard);

        assertThat(answer.selection()).containsExactly(FIRST_OFFER);
        assertThat(checkout.accepted).singleElement()
                .satisfies(handover -> assertThat(handover.selection())
                        .extracting(Handover.Line::offerId).containsExactly(FIRST_OFFER));
    }

    @Test
    void answersTheShapeOfRecordTheContractFixes() {
        baskets.save(aBasketWith(aFirstItem()));

        HandoverAnswer answer = service.handOver(BASKET_ID, Channel.standard);

        JsonAssert.assertThat(answer).isExactlyLike("""
                {
                  "handoverId": "B-7f3a",
                  "basketId": "B-7f3a",
                  "createdAt": "2026-11-26T10:00:00Z",
                  "customerId": "C-42",
                  "currency": "PLN",
                  "selection": ["OF-1001"],
                  "revision": 1,
                  "totals": {
                    "productValue": { "amount": 99890, "currency": "PLN", "kind": "charge" },
                    "discountValue": { "amount": 0, "currency": "PLN", "kind": "discount" },
                    "delivery": { "amount": 9990, "currency": "PLN", "kind": "charge" },
                    "totalWithDelivery": { "amount": 109880, "currency": "PLN", "kind": "charge" }
                  }
                }
                """);
    }

    @Test
    void answersTheSelectionInTheOrderTheIdentitySortsIt() {
        baskets.save(aBasketWith(aSecondItem(), aFirstItem()));

        HandoverAnswer answer = service.handOver(BASKET_ID, Channel.standard);

        assertThat(answer.selection()).containsExactly(FIRST_OFFER, SECOND_OFFER);
    }

    @Test
    void refusesABlockedRowAndLeavesTheBasketUntouched() {
        BasketId basketId = service.basketOf(CUSTOMER_ID).basketId();
        service.addToBasket(basketId, aFirstItem());
        service.addToBasket(basketId, anOutOfStockItem());
        Basket before = service.basket(basketId);

        assertThatThrownBy(() -> service.handOver(basketId, Channel.standard))
                .isInstanceOfSatisfying(ShoppingException.class, refused -> {
                    assertThat(refused.error()).isEqualTo(ShoppingException.Error.BLOCKED_BY_ITEMS);
                    assertThat(refused.details()).containsExactly(basketId.value(),
                            List.of(aRefusal(SECOND_OFFER, Availability.Status.outOfStock, false)));
                });

        assertThat(service.basket(basketId)).isEqualTo(before);
        assertThat(checkout.accepted).isEmpty();
    }

    @Test
    void namesTheBlockedRowInTheWireShapeOfReasons() {
        baskets.save(aBasketWith(aFirstItem(), anOutOfStockItem()));

        assertThatThrownBy(() -> service.handOver(BASKET_ID, Channel.standard))
                .isInstanceOfSatisfying(ShoppingException.class, refused -> JsonAssert
                        .assertThat(blockedRowOf(refused))
                        .isExactlyLike("{\"offerId\": \"OF-1002\", \"status\": \"outOfStock\", \"stale\": false}"));
    }

    private static Object blockedRowOf(ShoppingException refused) {
        return ((List<?>) refused.details().get(1)).get(0);
    }

    @Test
    void refusesAHandoverOfNothingSelected() {
        BasketId basketId = service.basketOf(CUSTOMER_ID).basketId();
        service.addToBasket(basketId, aFirstItem());
        service.select(basketId, selectionOf(FIRST_OFFER), false);

        assertThatThrownBy(() -> service.handOver(basketId, Channel.standard))
                .isInstanceOfSatisfying(ShoppingException.class, refused -> assertThat(refused.error())
                        .isEqualTo(ShoppingException.Error.NOTHING_SELECTED));
        assertThat(checkout.accepted).isEmpty();
    }

    @Test
    void refusesAHandoverOfAnArchivedBasket() {
        baskets.save(anArchivedBasketWith(aFirstItem()));

        assertThatThrownBy(() -> service.handOver(BASKET_ID, Channel.standard))
                .isInstanceOfSatisfying(ShoppingException.class, refused -> assertThat(refused.error())
                        .isEqualTo(ShoppingException.Error.BASKET_INACTIVE));
        assertThat(checkout.accepted).isEmpty();
    }

    @Test
    void handsOverARowWhoseStockCouldNotBeRead() {
        baskets.save(aBasketWith(anItemReadAsStale()));

        HandoverAnswer answer = service.handOver(BASKET_ID, Channel.standard);

        assertThat(answer.selection()).containsExactly(FIRST_OFFER);
    }

    @Test
    void crossesThePricesTheBasketComputed() {
        BasketId basketId = service.basketOf(CUSTOMER_ID).basketId();
        service.addToBasket(basketId, aFirstItem());
        service.changeQuantity(basketId, FIRST_OFFER, 2);

        service.handOver(basketId, Channel.standard);

        assertThat(checkout.accepted).singleElement().satisfies(handover ->
                assertThat(handover.selection()).singleElement().satisfies(line -> {
                    assertThat(line.currentPrice()).isEqualTo(aLastKnown().currentPrice());
                    assertThat(line.discount()).isEqualTo(Money.zero(PLN, Money.Kind.discount));
                    assertThat(line.lineTotal()).isEqualTo(aLastKnown().currentPrice().times(2));
                }));
    }

    @Test
    void answersTheSameHandoverForUnchangedContentWithoutAskingCheckoutAgain() {
        BasketId basketId = service.basketOf(CUSTOMER_ID).basketId();
        service.addToBasket(basketId, aFirstItem());

        HandoverAnswer first = service.handOver(basketId, Channel.standard);
        HandoverAnswer repeated = service.handOver(basketId, Channel.standard);

        assertThat(repeated).isEqualTo(first);
        assertThat(checkout.accepted).hasSize(1);
    }

    @Test
    void answersTheSameHandoverOnEitherSideOfTheWarsawMidnight() {
        baskets.save(aBasketWith(aFirstItem()));
        Instant afterMidnight = LATE_EVENING.plusSeconds(3_600);

        HandoverAnswer beforeMidnight = aServiceAt(LATE_EVENING, baskets, checkout).handOver(BASKET_ID, Channel.standard);
        HandoverAnswer acrossTheDay = aServiceAt(afterMidnight, baskets, checkout).handOver(BASKET_ID, Channel.standard);

        assertThat(anEstimate().estimate(aRequestOf(aFixedClockAt(LATE_EVENING), aFirstItem())))
                .isNotEqualTo(anEstimate().estimate(aRequestOf(aFixedClockAt(afterMidnight), aFirstItem())));
        assertThat(acrossTheDay).isEqualTo(beforeMidnight);
        assertThat(checkout.accepted).hasSize(1);
    }

    @Test
    void answersANewRevisionAfterTheContentChanged() {
        BasketId basketId = service.basketOf(CUSTOMER_ID).basketId();
        service.addToBasket(basketId, aFirstItem());
        HandoverAnswer first = service.handOver(basketId, Channel.standard);

        service.changeQuantity(basketId, FIRST_OFFER, 2);
        HandoverAnswer second = service.handOver(basketId, Channel.standard);

        assertThat(second.revision()).isEqualTo(first.revision() + 2);
        assertThat(second.handoverId()).isEqualTo(first.handoverId());
        assertThat(checkout.accepted).hasSize(2);
    }

    @Test
    void neverClearsOrLocksTheBasket() {
        BasketId basketId = service.basketOf(CUSTOMER_ID).basketId();
        service.addToBasket(basketId, aFirstItem());

        service.handOver(basketId, Channel.standard);
        Basket after = service.basket(basketId);

        assertThat(after.isActive()).isTrue();
        assertThat(after.items()).singleElement()
                .satisfies(item -> assertThat(item.selected()).isTrue());
        assertThat(service.changeQuantity(basketId, FIRST_OFFER, 3).items()).singleElement()
                .satisfies(item -> assertThat(item.quantity()).isEqualTo(3));
    }

    @Test
    void createsNoOrderOfItsOwn() {
        BasketId basketId = service.basketOf(CUSTOMER_ID).basketId();
        service.addToBasket(basketId, aFirstItem());

        service.handOver(basketId, Channel.standard);

        assertThat(baskets.emitted).hasSize(2);
        assertThat(baskets.emitted.get(1)).isInstanceOf(DomainEvent.GoToCheckout.class);
        assertThat(checkout.accepted).singleElement()
                .satisfies(handover -> assertThat(handover.selection()).hasSize(1));
    }

    @Test
    void pricesABasketInItsOwnCurrencyEndToEnd() {
        FakeBasketRepository euroBaskets = new FakeBasketRepository();
        BasketService euro = aServiceOf(new BasketCurrency(EUR), euroBaskets, checkout);
        BasketId basketId = euro.basketOf(CUSTOMER_ID).basketId();
        euro.addToBasket(basketId, anItemPricedIn(FIRST_OFFER, EUR));

        HandoverAnswer answer = euro.handOver(basketId, Channel.standard);

        assertThat(answer.currency()).isEqualTo(EUR);
        assertThat(answer.totals()).isEqualTo(totalsIn(EUR, 99890, 0, 9990));
        assertThat(checkout.accepted).singleElement()
                .satisfies(handover -> assertThat(handover.selection()).singleElement()
                        .satisfies(line -> assertThat(line.lineTotal().currency()).isEqualTo(EUR)));
    }

    @Test
    void answersANewRevisionWhenTheConfigurationChangedTheTotals() {
        baskets.save(aBasketWith(aFirstItem()));
        HandoverAnswer before = service.handOver(BASKET_ID, Channel.standard);

        HandoverAnswer after = aServiceCharging(new DeliveryRates(12_340, true), baskets, checkout)
                .handOver(BASKET_ID, Channel.standard);

        assertThat(after.totals().delivery().amount()).isEqualTo(12_340);
        assertThat(after.revision()).isGreaterThan(before.revision());
        assertThat(checkout.accepted).hasSize(2);
    }

    @Test
    void publishesTheOneDeliveryRangeOfABasketOfTwoShipments() {
        BasketId basketId = service.basketOf(CUSTOMER_ID).basketId();
        service.addToBasket(basketId, aFirstItem());
        service.addToBasket(basketId, aThirdItemOfAnotherSeller());

        HandoverAnswer answer = service.handOver(basketId, Channel.standard);

        assertThat(answer.totals()).isEqualTo(totalsOf(2 * 99890, 0, 2 * 9990));
        assertThat(checkout.accepted).singleElement().satisfies(handover ->
                assertThat(handover.deliveryEstimate()).isEqualTo(anEstimatedRange(FROM, TO)));
    }
}
