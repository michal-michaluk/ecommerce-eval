package shop.shopping.basket;

import org.junit.jupiter.api.Test;

import java.util.List;

import shop.shopping.JsonAssert;
import shop.shopping.kernel.Availability;
import shop.shopping.kernel.ShoppingException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static shop.shopping.basket.BasketFixture.BASKET_ID;
import static shop.shopping.basket.BasketFixture.CUSTOMER_ID;
import static shop.shopping.basket.BasketFixture.FIRST_OFFER;
import static shop.shopping.basket.BasketFixture.SECOND_OFFER;
import static shop.shopping.basket.BasketFixture.NOW;
import static shop.shopping.basket.BasketFixture.aBasketCurrency;
import static shop.shopping.basket.BasketFixture.aBasketWith;
import static shop.shopping.basket.BasketFixture.aCaller;
import static shop.shopping.basket.BasketFixture.aCatalog;
import static shop.shopping.basket.BasketFixture.aFirstItem;
import static shop.shopping.basket.BasketFixture.aSecondItem;
import static shop.shopping.basket.BasketFixture.aServiceOf;
import static shop.shopping.basket.OfferCatalogFixture.A_DISCOUNTED_AMOUNT;
import static shop.shopping.basket.OfferCatalogFixture.OFFER;
import static shop.shopping.basket.OfferCatalogFixture.SOME_UNITS;
import static shop.shopping.basket.OfferCatalogFixture.aFailedRead;
import static shop.shopping.basket.OfferCatalogFixture.anOfferOnSaleWithStock;
import static shop.shopping.basket.OfferCatalogFixture.anOfferPricedIn;
import static shop.shopping.basket.OfferCatalogFixture.aWithdrawnOffer;
import static shop.shopping.basket.OfferCatalogFixture.withoutTitle;

/**
 * The two things {@code PATCH /baskets/{basketId}/items/{offerId}} owns that no aggregate can (D28, F16): an offer the basket
 * does not hold is read before it becomes a row, and the command is an absolute quantity rather than a change.
 *
 * <p>The four offers nobody can add are the four answers element 07 §Error contract names — {@code 422 CURRENCY_MISMATCH},
 * {@code 404 OFFER_NOT_FOUND}, {@code 503 OFFER_READ_UNAVAILABLE} and the same {@code 503} for an answer that describes nothing
 * a row could render. The render's half is the pull: one batched read, an answer applied through the ingestion path, a failed
 * read greying its row instead of failing the basket, and the document written because a render is the deviation D62 records.
 */
class BasketOfferAddTest {

    /** One row added from a catalog answer, at the quantity the client stated, with its discount reported beside the total. */
    private static final String ONE_ADDED_ROW = """
            {
              "items": [
                { "offerId": "OF-1001", "seller": "UGREEN_POLSKA", "quantity": 2, "selected": true,
                  "availability": { "status": "available", "stale": false },
                  "currentPrice": { "amount": 99890, "currency": "PLN", "kind": "charge" },
                  "discount": { "amount": 10000, "currency": "PLN", "kind": "discount" },
                  "lineTotal": { "amount": 199780, "currency": "PLN", "kind": "charge" } }
              ],
              "totals": { "productValue": { "amount": 199780 }, "discountValue": { "amount": 20000 },
                          "totalWithDelivery": { "amount": 209770 } }
            }
            """;

    final FakeBasketRepository baskets = new FakeBasketRepository();
    final FakeCheckoutHandovers checkout = new FakeCheckoutHandovers();

    @Test
    void addsAnOfferTheBasketDoesNotHoldYetAtTheQuantityTheClientStates() {
        BasketService service = aServiceSelling(anOfferOnSaleWithStock(SOME_UNITS));
        BasketId basketId = service.basketOf(CUSTOMER_ID).basketId();

        BasketContent content = service.setQuantity(basketId, OFFER, 2, aCaller());

        JsonAssert.assertThat(content).hasFieldsLike(ONE_ADDED_ROW);
        assertThat(content.items()).singleElement()
                .satisfies(item -> assertThat(item.discount()).isEqualTo(A_DISCOUNTED_AMOUNT));
    }

    @Test
    void repeatsTheQuantityTheCommandStatesInsteadOfAddingToIt() {
        BasketService service = aServiceSelling(anOfferOnSaleWithStock(SOME_UNITS));
        BasketId basketId = service.basketOf(CUSTOMER_ID).basketId();
        service.setQuantity(basketId, OFFER, 3, aCaller());

        BasketContent content = service.setQuantity(basketId, OFFER, 1, aCaller());

        assertThat(content.items()).singleElement()
                .satisfies(item -> assertThat(item.quantity()).isEqualTo(1));
    }

    @Test
    void refusesAnOfferPricedInAnotherCurrency() {
        baskets.save(aBasketWith());
        BasketService service = aServiceSelling(anOfferPricedIn("EUR"));

        assertThatThrownBy(() -> service.setQuantity(BASKET_ID, OFFER, 1, aCaller()))
                .isInstanceOfSatisfying(ShoppingException.class, refused ->
                        assertThat(refused.error()).isEqualTo(ShoppingException.Error.CURRENCY_MISMATCH));
    }

    @Test
    void refusesAnOfferTheProviderNoLongerHas() {
        baskets.save(aBasketWith());
        BasketService service = aServiceSelling(aWithdrawnOffer());

        assertThatThrownBy(() -> service.setQuantity(BASKET_ID, OFFER, 1, aCaller()))
                .isInstanceOfSatisfying(ShoppingException.class, refused ->
                        assertThat(refused.error()).isEqualTo(ShoppingException.Error.OFFER_NOT_FOUND));
    }

    @Test
    void refusesAnOfferNobodyManagedToRead() {
        baskets.save(aBasketWith());
        BasketService service = aServiceSelling(aFailedRead());

        assertThatThrownBy(() -> service.setQuantity(BASKET_ID, OFFER, 1, aCaller()))
                .isInstanceOfSatisfying(ShoppingException.class, refused ->
                        assertThat(refused.error()).isEqualTo(ShoppingException.Error.OFFER_READ_UNAVAILABLE));
    }

    @Test
    void refusesAnAnswerThatDescribesNothingARowCouldRender() {
        baskets.save(aBasketWith());
        BasketService service = aServiceSelling(withoutTitle());

        assertThatThrownBy(() -> service.setQuantity(BASKET_ID, OFFER, 1, aCaller()))
                .isInstanceOfSatisfying(ShoppingException.class, refused ->
                        assertThat(refused.error()).isEqualTo(ShoppingException.Error.OFFER_READ_UNAVAILABLE));
    }

    @Test
    void readsEveryOfferOfOneBasketInASingleCallOnRender() {
        FixtureOfferCatalog catalog = aCatalog();
        catalog.sells(FIRST_OFFER, SOME_UNITS);
        catalog.sells(SECOND_OFFER, SOME_UNITS);
        baskets.save(aBasketWith(aFirstItem(), aSecondItem()));

        aServiceOf(aBasketCurrency(), NOW, baskets, catalog, checkout).render(BASKET_ID, aCaller());

        assertThat(catalog.reads()).containsExactly(List.of(FIRST_OFFER, SECOND_OFFER));
    }

    @Test
    void greysTheRowOfAReadThatFailedInsteadOfFailingTheRender() {
        FixtureOfferCatalog catalog = aCatalog();
        catalog.failingWith(FixtureOfferCatalog.Failure.timeout);
        baskets.save(aBasketWith(aFirstItem()));

        BasketContent content = aServiceOf(aBasketCurrency(), NOW, baskets, catalog, checkout)
                .render(BASKET_ID, aCaller());

        assertThat(content.items()).singleElement().satisfies(item -> {
            assertThat(item.availability().status()).isEqualTo(Availability.Status.unavailable);
            assertThat(item.availability().stale()).isTrue();
            assertThat(item.lineTotal().amount()).isEqualTo(99890);
        });
    }

    @Test
    void writesTheBasketWhenThePullChangedWhatTheRowSays() {
        FixtureOfferCatalog catalog = aCatalog();
        catalog.sells(FIRST_OFFER, SOME_UNITS);
        baskets.save(aBasketWith(aFirstItem()));
        baskets.saved.clear();

        BasketContent answer = aServiceOf(aBasketCurrency(), NOW, baskets, catalog, checkout)
                .render(BASKET_ID, aCaller());

        assertThat(baskets.saved).containsExactly(BASKET_ID.value());
        assertThat(answer.items()).singleElement()
                .satisfies(item -> assertThat(item.seller()).isEqualTo(FixtureOfferCatalog.SELLER));
    }

    @Test
    void writesNothingWhenNeitherTheCommandNorThePullChangedAnything() {
        FixtureOfferCatalog catalog = aCatalog();
        catalog.sells(FIRST_OFFER, SOME_UNITS);
        BasketService service = aServiceOf(aBasketCurrency(), NOW, baskets, catalog, checkout);
        BasketId basketId = service.basketOf(CUSTOMER_ID).basketId();
        service.setQuantity(basketId, OFFER, 1, aCaller());
        long writtenOnce = revisionOf(basketId);
        baskets.saved.clear();

        service.setQuantity(basketId, OFFER, 1, aCaller());

        assertThat(baskets.saved).isEmpty();
        assertThat(revisionOf(basketId)).isEqualTo(writtenOnce);
    }

    /** The persisted document's own revision — the number the basket moves only when it is mutated (D35, D59). */
    private long revisionOf(BasketId basketId) {
        return baskets.stored(basketId).toSnapshot().revision();
    }

    /** The primary port reading one answer per asked id, so a test states the catalog's whole vocabulary in one place. */
    private BasketService aServiceSelling(OfferRecord... offers) {
        return aServiceOf(aBasketCurrency(), NOW, baskets, requested -> List.of(offers), checkout);
    }
}
