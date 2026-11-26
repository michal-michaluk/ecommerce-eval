package shop.shopping.basket;

import shop.shopping.kernel.Availability;
import shop.shopping.kernel.Money;

/**
 * Element 04's acceptance table as test data: the two offers of the capture at the prices the table names (99,89 and 64,99),
 * the third one that makes a second seller, the three availability outcomes and the catalog discount of S7.
 *
 * <p>Every row is built here rather than inline, because a row is wider than two parameters ({@code test.new-needs-factory}),
 * and because a test should read in the vocabulary of its own row — {@code theSecondItemOutOfStock()} says which of the twelve
 * rows it belongs to. Each offer keeps the one seller it belongs to (element 02 RULE-4, RULE-7): the second seller is a third
 * offer, never a second seller of an offer the capture already placed.
 */
public class BasketPriceFixture {

    public static final long FIRST_PRICE = 99890;
    public static final long SECOND_PRICE = 64990;
    public static final long DISCOUNTED_FIRST_PRICE = 89890;
    public static final long DISCOUNT = 10000;
    public static final long DELIVERY_RATE = 9990;
    public static final String EUR = "EUR";

    private static final String TITLE = "UGREEN Nexode Szybka Ładowarka Sieciowa USB 2x USB Typ C Zasilacz QC PD 65W";
    private static final String PICTURE = "img-1001";

    private BasketPriceFixture() {
    }

    public static BasketItem theFirstItem() {
        return anItem(BasketFixture.FIRST_OFFER, BasketFixture.UGREEN, anAmount(FIRST_PRICE));
    }

    public static BasketItem theSecondItem() {
        return anItem(BasketFixture.SECOND_OFFER, BasketFixture.UGREEN, anAmount(SECOND_PRICE));
    }

    public static BasketItem theThirdItemOfAnotherSeller() {
        return anItemOfAnotherStatus(BasketFixture.THIRD_OFFER, BasketFixture.ANKER,
                Availability.available(), anAmount(SECOND_PRICE));
    }

    public static BasketItem theSecondItemOutOfStock() {
        return anItemOfAnotherStatus(BasketFixture.SECOND_OFFER, BasketFixture.UGREEN,
                Availability.outOfStock(), anAmount(SECOND_PRICE));
    }

    public static BasketItem anUnavailableItemOfAnotherSeller() {
        return anItemOfAnotherStatus(BasketFixture.THIRD_OFFER, BasketFixture.ANKER,
                Availability.removed(), anAmount(SECOND_PRICE));
    }

    public static BasketItem theFirstItemWithCatalogDiscount() {
        return new BasketItem(BasketFixture.FIRST_OFFER, BasketFixture.UGREEN, 1, true, Availability.available(),
                new BasketItem.LastKnown(TITLE, PICTURE, anAmount(DISCOUNTED_FIRST_PRICE), aDiscount(DISCOUNT)));
    }

    public static BasketItem aFirstItemInAnotherCurrency() {
        return new BasketItem(BasketFixture.FIRST_OFFER, BasketFixture.UGREEN, 1, true, Availability.available(),
                new BasketItem.LastKnown(TITLE, PICTURE, new Money(FIRST_PRICE, EUR),
                        Money.zero(EUR, Money.Kind.discount)));
    }

    public static BasketItem anItemWithQuantity(int quantity) {
        return theFirstItem().withQuantity(quantity);
    }

    public static BasketItem twice(BasketItem item) {
        return item.withQuantity(2);
    }

    public static BasketItem deselected(BasketItem item) {
        return item.withSelection(false);
    }

    public static Basket aBasketOf(BasketItem... items) {
        return BasketFixture.aSnapshotOf(BasketFixture.aBasketWith(items));
    }

    public static BasketPriceCalculation aCalculation() {
        return BasketPriceCalculation.defaultCalculation();
    }

    public static BasketPriceCalculation aCalculationThatChargesSmart() {
        return new BasketPriceCalculation(new DeliveryRates(DELIVERY_RATE, false));
    }

    public static Money anAmount(long fineUnits) {
        return new Money(fineUnits, BasketFixture.PLN);
    }

    public static Money aDiscount(long fineUnits) {
        return new Money(fineUnits, BasketFixture.PLN, Money.Kind.discount);
    }

    public static BasketPrice aPrice(long productValue, long discountValue, long deliveryValue, long total) {
        return new BasketPrice(anAmount(productValue), aDiscount(discountValue),
                anAmount(deliveryValue), anAmount(total));
    }

    private static BasketItem anItem(String offerId, String seller, Money currentPrice) {
        return new BasketItem(offerId, seller, 1, true, Availability.available(),
                new BasketItem.LastKnown(TITLE, PICTURE, currentPrice, aDiscount(0)));
    }

    private static BasketItem anItemOfAnotherStatus(String offerId, String seller, Availability availability,
            Money currentPrice) {
        return new BasketItem(offerId, seller, 1, true, availability,
                new BasketItem.LastKnown(TITLE, PICTURE, currentPrice, aDiscount(0)));
    }
}
