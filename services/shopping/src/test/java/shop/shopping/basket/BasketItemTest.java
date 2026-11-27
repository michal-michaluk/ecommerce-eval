package shop.shopping.basket;

import org.junit.jupiter.api.Test;

import shop.shopping.kernel.Availability;
import shop.shopping.kernel.ShoppingException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static shop.shopping.basket.BasketFixture.ANKER;
import static shop.shopping.basket.BasketFixture.FIRST_OFFER;
import static shop.shopping.basket.BasketFixture.NOW;
import static shop.shopping.basket.BasketFixture.UGREEN;
import static shop.shopping.basket.BasketFixture.aFirstItem;
import static shop.shopping.basket.BasketFixture.aLastKnown;
import static shop.shopping.basket.BasketFixture.aLastKnownPricedInTwoCurrencies;
import static shop.shopping.basket.BasketFixture.aSecondItem;
import static shop.shopping.basket.BasketFixture.anItem;
import static shop.shopping.basket.BasketFixture.anItemReadAt;
import static shop.shopping.basket.OfferCatalogFixture.DISCOUNT;
import static shop.shopping.basket.OfferCatalogFixture.LIST_PRICE;
import static shop.shopping.basket.OfferCatalogFixture.aCommandOf;
import static shop.shopping.basket.OfferCatalogFixture.aDiscountChangeIn;
import static shop.shopping.basket.OfferCatalogFixture.aPriceChangeIn;
import static shop.shopping.basket.OfferCatalogFixture.withoutAPrice;

/**
 * Element 02's row on its own: what a snapshot may be priced in (RULE-2, D63), what applying an observation does to it
 * (RULE-8), what a repeated add may reach (RULE-3, RULE-4) and what makes two rows the same row (element 02 §revision).
 *
 * <p>These are the answers no route reaches on its own: a row is built from what the provider described, so an answer that
 * names no price, an answer pegged in another currency and a quantity that passes {@code int} are the shapes only a provider
 * or a corrupted document states. The application path itself is proved one level up, in {@code OfferIngestionTest}.
 */
class BasketItemTest {

    @Test
    void keepsBothPricesWhenTheAnswerNamesNone() {
        BasketItem observed = aFirstItem().applying(aCommandOf(withoutAPrice()));

        assertThat(observed.lastKnown().currentPrice()).isEqualTo(aLastKnown().currentPrice());
        assertThat(observed.lastKnown().discount()).isEqualTo(aLastKnown().discount());
    }

    @Test
    void keepsItsPricesWhenAPriceChangeIsReportedInAnotherCurrency() {
        BasketItem observed = aFirstItem().applying(aPriceChangeIn(FIRST_OFFER, NOW, "EUR", LIST_PRICE));

        assertThat(observed.lastKnown().currentPrice()).isEqualTo(aLastKnown().currentPrice());
        assertThat(observed.lastKnown().discount()).isEqualTo(aLastKnown().discount());
    }

    @Test
    void keepsItsPricesWhenADiscountIsReportedInAnotherCurrency() {
        BasketItem observed = aFirstItem().applying(aDiscountChangeIn(FIRST_OFFER, NOW, "EUR", DISCOUNT));

        assertThat(observed.lastKnown().currentPrice()).isEqualTo(aLastKnown().currentPrice());
        assertThat(observed.lastKnown().discount()).isEqualTo(aLastKnown().discount());
    }

    @Test
    void refusesASnapshotPricedInOneCurrencyAndDiscountedInAnother() {
        assertThatThrownBy(BasketFixture::aLastKnownPricedInTwoCurrencies)
                .isInstanceOfSatisfying(ShoppingException.class, refused -> assertThat(refused.error())
                        .isEqualTo(ShoppingException.Error.CURRENCY_MISMATCH));
    }

    @Test
    void refusesARepeatedAddThatWouldPassTheQuantityRange() {
        BasketItem allButOneUnit = anItem(FIRST_OFFER, UGREEN, Integer.MAX_VALUE, Availability.available());

        assertThatThrownBy(() -> allButOneUnit.merging(1))
                .isInstanceOfSatisfying(ShoppingException.class, refused -> assertThat(refused.error())
                        .isEqualTo(ShoppingException.Error.QUANTITY_OUT_OF_RANGE));
    }

    @Test
    void tellsTwoRowsApartByTheirSelection() {
        assertThat(aFirstItem().withSelection(false).saysTheSameAs(aFirstItem())).isFalse();
    }

    @Test
    void tellsTwoRowsApartByTheirQuantity() {
        assertThat(aFirstItem().withQuantity(2).saysTheSameAs(aFirstItem())).isFalse();
    }

    @Test
    void tellsTwoRowsApartByTheirAvailability() {
        assertThat(anItem(FIRST_OFFER, UGREEN, 1, Availability.outOfStock()).saysTheSameAs(aFirstItem())).isFalse();
    }

    @Test
    void tellsTwoRowsApartByTheSellerTheyAreGroupedUnder() {
        assertThat(anItem(FIRST_OFFER, ANKER, 1, Availability.available()).saysTheSameAs(aFirstItem())).isFalse();
    }

    @Test
    void tellsTwoRowsApartByTheirIdentity() {
        assertThat(aSecondItem().saysTheSameAs(aFirstItem())).isFalse();
    }

    @Test
    void treatsARowOfTheSameContentReadAtAnotherInstantAsTheSameRow() {
        assertThat(anItemReadAt(FIRST_OFFER, NOW.plusSeconds(60)).saysTheSameAs(aFirstItem())).isTrue();
    }
}
