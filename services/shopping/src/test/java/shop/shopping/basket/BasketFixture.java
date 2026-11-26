package shop.shopping.basket;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import shop.shopping.kernel.Availability;
import shop.shopping.kernel.Money;
import shop.shopping.kernel.MoneyFixture;

/**
 * The basket of element 02 as its tests build it: the two offers of the capture, a third one from another seller, the three
 * availability outcomes and the two basket states.
 *
 * <p>An item is built with its own fixture factory rather than inline, because a row is wider than two parameters
 * ({@code test.new-needs-factory}) and because every test should read in the vocabulary of the element it proves.
 */
public class BasketFixture {

    public static final BasketId BASKET_ID = new BasketId("B-7f3a");
    public static final CustomerId CUSTOMER_ID = new CustomerId("C-42");
    public static final String PLN = MoneyFixture.PLN;
    public static final Instant NOW = Instant.parse("2026-11-26T10:00:00Z");
    public static final String UGREEN = "UGREEN_POLSKA";
    public static final String ANKER = "ANKER_STORE";
    public static final String FIRST_OFFER = "OF-1001";
    public static final String SECOND_OFFER = "OF-1002";
    public static final String THIRD_OFFER = "OF-2007";

    private BasketFixture() {
    }

    public static BasketEditor anEmptyBasket() {
        return BasketEditor.openBasketFor(BASKET_ID, CUSTOMER_ID, PLN);
    }

    public static BasketEditor aBasketWith(BasketItem... items) {
        return new BasketEditor(BASKET_ID, new ArrayList<>(), CUSTOMER_ID, PLN,
                new ArrayList<>(List.of(items)), BasketState.active, 0, null);
    }

    public static BasketEditor anArchivedBasketWith(BasketItem... items) {
        return new BasketEditor(BASKET_ID, new ArrayList<>(), CUSTOMER_ID, PLN,
                new ArrayList<>(List.of(items)), BasketState.archived, 0, null);
    }

    public static BasketEditor aBasketFor(BasketId basketId, CustomerId customerId) {
        return BasketEditor.openBasketFor(basketId, customerId, PLN);
    }

    public static BasketEditor anArchivedBasketFor(BasketId basketId, CustomerId customerId, BasketItem... items) {
        return new BasketEditor(basketId, new ArrayList<>(), customerId, PLN,
                new ArrayList<>(List.of(items)), BasketState.archived, 0, null);
    }

    public static BasketId anotherBasketId() {
        return new BasketId("B-another");
    }

    public static BasketItem aFirstItem() {
        return anItem(FIRST_OFFER, UGREEN, 1, Availability.available());
    }

    public static BasketItem aSecondItem() {
        return anItem(SECOND_OFFER, UGREEN, 1, Availability.available());
    }

    public static BasketItem aThirdItemOfAnotherSeller() {
        return anItem(THIRD_OFFER, ANKER, 1, Availability.available());
    }

    public static BasketItem anItem(String offerId, String seller, int quantity, Availability availability) {
        return new BasketItem(offerId, seller, quantity, true, availability, aLastKnown());
    }

    public static BasketItem anItemReadAsStale() {
        return anItem(FIRST_OFFER, UGREEN, 1, Availability.availableWithoutStockRead());
    }

    public static BasketItem anOutOfStockItem() {
        return anItem(SECOND_OFFER, UGREEN, 1, Availability.outOfStock());
    }

    public static BasketItem anUnavailableItem() {
        return anItem(THIRD_OFFER, ANKER, 1, Availability.removed());
    }

    public static BasketItem anItemInAnotherCurrency() {
        return new BasketItem(FIRST_OFFER, UGREEN, 1, true, Availability.available(),
                new BasketItem.LastKnown("UGREEN Nexode", "img-1001",
                        MoneyFixture.anAmountIn(99890, "EUR"), Money.zero("EUR", Money.Kind.discount)));
    }

    public static BasketItem.LastKnown aLastKnown() {
        return new BasketItem.LastKnown(
                "UGREEN Nexode Szybka Ładowarka Sieciowa USB 2x USB Typ C Zasilacz QC PD 65W",
                "img-1001", MoneyFixture.aCurrentPrice(), Money.zero(PLN, Money.Kind.discount));
    }

    public static BasketSelection.Item selectionOf(String offerId) {
        return new BasketSelection.Item(offerId);
    }

    public static BasketSelection.Shipment shipmentOf(String seller) {
        return new BasketSelection.Shipment(seller);
    }

    public static BasketSelection.All wholeBasket() {
        return new BasketSelection.All();
    }

    public static Basket aSnapshotOf(BasketEditor basket) {
        return basket.toSnapshot();
    }

    public static BasketCurrency aBasketCurrency() {
        return new BasketCurrency(PLN);
    }

    public static CustomerId aFreshCustomer() {
        return new CustomerId("C-" + UUID.randomUUID().toString().substring(0, 8));
    }
}
