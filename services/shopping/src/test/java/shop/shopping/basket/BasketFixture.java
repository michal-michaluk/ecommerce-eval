package shop.shopping.basket;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import shop.shopping.kernel.Availability;
import shop.shopping.kernel.CheckoutHandovers;
import shop.shopping.kernel.Money;
import shop.shopping.kernel.MoneyFixture;
import shop.shopping.kernel.ShoppingException;

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
    public static final String EUR = "EUR";
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

    public static BasketEditor aBasketWithIn(String currency, BasketItem... items) {
        return new BasketEditor(BASKET_ID, new ArrayList<>(), CUSTOMER_ID, currency,
                new ArrayList<>(List.of(items)), BasketState.active, 0, null);
    }

    /** The basket of one test, under an identity no other test hands over — the handover key is the basket's (D32). */
    public static BasketEditor aBasketOf(BasketId basketId, CustomerId customerId, BasketItem... items) {
        return new BasketEditor(basketId, new ArrayList<>(), customerId, PLN,
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
        return new BasketItem(offerId, seller, quantity, true, availability, aLastKnown(), NOW);
    }

    public static BasketItem anItemReadAt(String offerId, Instant readAt) {
        return new BasketItem(offerId, UGREEN, 1, true, Availability.available(), aLastKnown(), readAt);
    }

    /** The same capture offer priced in another currency — the one a row of a non-PLN basket holds (D21). */
    public static BasketItem anItemPricedIn(String offerId, String currency) {
        return new BasketItem(offerId, UGREEN, 1, true, Availability.available(),
                new BasketItem.LastKnown("UGREEN Nexode 65W", "img-1001", new Money(99890, currency),
                        Money.zero(currency, Money.Kind.discount)), NOW);
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
                        MoneyFixture.anAmountIn(99890, "EUR"), Money.zero("EUR", Money.Kind.discount)), NOW);
    }

    public static BasketItem aRowFromBeforeTheReadInstantWasKept() {
        return new BasketItem(FIRST_OFFER, UGREEN, 1, true, Availability.available(), aLastKnown(), null);
    }

    /**
     * One row's snapshot priced in one currency and discounted in another — the pair no row may hold (D63, element 03
     * RULE-2), so building it is the refusal under test.
     *
     * @return the snapshot as the row would have parsed it
     */
    public static BasketItem.LastKnown aLastKnownPricedInTwoCurrencies() {
        return new BasketItem.LastKnown("UGREEN Nexode", "img-1001",
                MoneyFixture.aCurrentPrice(), Money.zero(EUR, Money.Kind.discount));
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

    /** The primary port as a test drives it: the fake storage, the two policies of elements 04 and 05, and a recording port. */
    public static BasketService aServiceWith(FakeBasketRepository baskets, CheckoutHandovers checkouts) {
        return aServiceAt(NOW, baskets, checkouts);
    }

    /** The same port reading another instant — the only thing a calendar day changes. */
    public static BasketService aServiceAt(Instant now, FakeBasketRepository baskets, CheckoutHandovers checkouts) {
        return aServiceOf(aBasketCurrency(), now, baskets, checkouts);
    }

    public static BasketService aServiceOf(BasketCurrency currency, FakeBasketRepository baskets,
            CheckoutHandovers checkouts) {
        return aServiceOf(currency, NOW, baskets, checkouts);
    }

    public static BasketService aServiceOf(BasketCurrency currency, Instant now, FakeBasketRepository baskets,
            CheckoutHandovers checkouts) {
        return aServiceOf(currency, now, baskets, anInertCatalog(), checkouts);
    }

    /** The same port reading the offers through the catalog a test seeded — the pull and the add path read it. */
    public static BasketService aServiceOf(BasketCurrency currency, Instant now, FakeBasketRepository baskets,
            OfferCatalog offers, CheckoutHandovers checkouts) {
        return new BasketService(baskets, currency, new BasketPriceCalculation(DeliveryRates.defaultRates()),
                BasketDeliveryEstimate.defaultEstimate(), offers, checkouts, Clock.fixed(now, ZoneOffset.UTC));
    }

    /** The primary port charging a delivery another deployment states — the same rows, another payload. */
    public static BasketService aServiceCharging(DeliveryRates rates, FakeBasketRepository baskets,
            CheckoutHandovers checkouts) {
        return new BasketService(baskets, aBasketCurrency(), new BasketPriceCalculation(rates),
                BasketDeliveryEstimate.defaultEstimate(), anInertCatalog(), checkouts, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    /** The catalog of element 08 as this increment has it, wanting a seed: an id nobody seeds is an offer nobody sells. */
    public static FixtureOfferCatalog aCatalog() {
        return new FixtureOfferCatalog(Clock.fixed(NOW, ZoneOffset.UTC));
    }

    /**
     * The catalog a test that is not about the pull wants: it answers nothing about any id — the provider refused the call — so
     * the pull leaves every row exactly as the test built it (element 08 §Failure mapping).
     */
    public static OfferCatalog anInertCatalog() {
        return requested -> List.of();
    }

    /** The caller every unit test acts for: the fixture customer, arriving the ordinary way. */
    public static Caller aCaller() {
        return aCallerOf(CUSTOMER_ID);
    }

    /** The same caller for another customer — the one a test of ownership needs (D68). */
    public static Caller aCallerOf(CustomerId customerId) {
        return new Caller(customerId, Channel.standard);
    }

    /** The service's one failure, so a test states a code and its domain values and nothing else (register D48). */
    public static ShoppingException aRefusalOf(ShoppingException.Error error, Object... details) {
        return new ShoppingException(error, details);
    }

    public static CustomerId aFreshCustomer() {
        return new CustomerId("C-" + UUID.randomUUID().toString().substring(0, 8));
    }
}
