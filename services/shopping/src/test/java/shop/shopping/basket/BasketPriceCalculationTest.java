package shop.shopping.basket;

import org.junit.jupiter.api.Test;

import shop.shopping.kernel.ShoppingException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static shop.shopping.basket.BasketPriceFixture.DELIVERY_RATE;
import static shop.shopping.basket.BasketPriceFixture.DISCOUNT;
import static shop.shopping.basket.BasketPriceFixture.EUR;
import static shop.shopping.basket.BasketPriceFixture.aBasketOf;
import static shop.shopping.basket.BasketPriceFixture.aBasketOfIn;
import static shop.shopping.basket.BasketPriceFixture.aCalculation;
import static shop.shopping.basket.BasketPriceFixture.aCalculationThatChargesSmart;
import static shop.shopping.basket.BasketPriceFixture.aDiscount;
import static shop.shopping.basket.BasketPriceFixture.aFirstItemInAnotherCurrency;
import static shop.shopping.basket.BasketPriceFixture.aPrice;
import static shop.shopping.basket.BasketPriceFixture.aPriceIn;
import static shop.shopping.basket.BasketPriceFixture.anAmount;
import static shop.shopping.basket.BasketPriceFixture.anItemWithQuantity;
import static shop.shopping.basket.BasketFixture.anItemPricedIn;
import static shop.shopping.basket.BasketPriceFixture.anUnavailableItemOfAnotherSeller;
import static shop.shopping.basket.BasketPriceFixture.deselected;
import static shop.shopping.basket.BasketPriceFixture.theFirstItem;
import static shop.shopping.basket.BasketPriceFixture.theFirstItemWithCatalogDiscount;
import static shop.shopping.basket.BasketPriceFixture.theSecondItem;
import static shop.shopping.basket.BasketPriceFixture.theSecondItemOutOfStock;
import static shop.shopping.basket.BasketPriceFixture.theThirdItemOfAnotherSeller;
import static shop.shopping.basket.BasketPriceFixture.twice;

/**
 * Element 04's acceptance table S1–S12, one test per row: the two offers of the capture priced over the selection (S1), the
 * selection being the subject (S2, S12), multiplication before summation (S3), an {@code outOfStock} row counted while
 * selected (S4) and an {@code unavailable} one excluded — from the shipment count too (S5), the SMART variant (S6), the
 * catalog discount reported and never double-counted (S7), one rate per seller shipment (S8), nothing selected as
 * {@code 0,00 zł} (S9) and the two refusals the item and the money types raise themselves (S10, S11).
 *
 * <p>Two further tests pin what no row of the table isolates: the discount line is multiplied by the quantity as well, and a
 * configuration that does not free the SMART channel keeps charging it.
 */
class BasketPriceCalculationTest {

    @Test
    void s1SumsBothSelectedItemsAndChargesOneShipment() {
        BasketPrice price = aCalculation().calculate(aBasketOf(theFirstItem(), theSecondItem()), Channel.standard);

        assertThat(price).isEqualTo(aPrice(164880, 0, DELIVERY_RATE, 174870));
    }

    @Test
    void s2PricesOnlyTheSelectedItem() {
        BasketPrice price = aCalculation()
                .calculate(aBasketOf(theFirstItem(), deselected(theSecondItem())), Channel.standard);

        assertThat(price).isEqualTo(aPrice(99890, 0, DELIVERY_RATE, 109880));
    }

    @Test
    void s3MultipliesTheUnitPriceByTheQuantity() {
        BasketPrice price = aCalculation().calculate(aBasketOf(twice(theFirstItem()), theSecondItem()), Channel.standard);

        assertThat(price).isEqualTo(aPrice(264770, 0, DELIVERY_RATE, 274760));
    }

    @Test
    void s4CountsAnOutOfStockRowWhileItIsSelected() {
        BasketPrice price = aCalculation()
                .calculate(aBasketOf(theFirstItem(), theSecondItemOutOfStock()), Channel.standard);

        assertThat(price).isEqualTo(aPrice(164880, 0, DELIVERY_RATE, 174870));
    }

    @Test
    void s5ExcludesAnUnavailableRow() {
        BasketPrice price = aCalculation()
                .calculate(aBasketOf(theFirstItem(), anUnavailableItemOfAnotherSeller()), Channel.standard);

        assertThat(price).isEqualTo(aPrice(99890, 0, DELIVERY_RATE, 109880));
    }

    @Test
    void s6ChargesNothingForDeliveryOnSmart() {
        BasketPrice price = aCalculation().calculate(aBasketOf(theFirstItem(), theSecondItem()), Channel.smart);

        assertThat(price).isEqualTo(aPrice(164880, 0, 0, 164880));
    }

    @Test
    void s7ReportsTheCatalogDiscountWithoutAddingItToTheTotal() {
        BasketPrice price = aCalculation()
                .calculate(aBasketOf(theFirstItemWithCatalogDiscount(), theSecondItem()), Channel.standard);

        assertThat(price.productValue()).isEqualTo(anAmount(154880));
        assertThat(price.discountValue()).isEqualTo(aDiscount(DISCOUNT));
        assertThat(price.deliveryValue()).isEqualTo(anAmount(DELIVERY_RATE));
        assertThat(price.totalWithDelivery()).isEqualTo(anAmount(164870));
    }

    @Test
    void s8ChargesOneRatePerSellerShipment() {
        BasketPrice price = aCalculation()
                .calculate(aBasketOf(theFirstItem(), theThirdItemOfAnotherSeller()), Channel.standard);

        assertThat(price).isEqualTo(aPrice(164880, 0, 19980, 184860));
    }

    @Test
    void s9PricesNothingSelectedAsZeroAmounts() {
        BasketPriceCalculation calculation = aCalculation();
        Basket nothingSelected = aBasketOf(deselected(theFirstItem()), deselected(theSecondItem()));

        assertThat(calculation.calculate(nothingSelected, Channel.standard)).isEqualTo(aPrice(0, 0, 0, 0));
        assertThat(calculation.calculate(aBasketOf(), Channel.standard)).isEqualTo(aPrice(0, 0, 0, 0));
    }

    @Test
    void s10RefusesAZeroQuantity() {
        assertThatThrownBy(() -> anItemWithQuantity(0))
                .isInstanceOfSatisfying(ShoppingException.class, refused -> assertThat(refused.error())
                        .isEqualTo(ShoppingException.Error.QUANTITY_NOT_POSITIVE));
    }

    @Test
    void s11RefusesAnItemPricedInAnotherCurrency() {
        BasketPriceCalculation calculation = aCalculation();
        Basket basket = aBasketOf(aFirstItemInAnotherCurrency());

        assertThatThrownBy(() -> calculation.calculate(basket, Channel.standard))
                .isInstanceOfSatisfying(ShoppingException.class, refused -> assertThat(refused.error())
                        .isEqualTo(ShoppingException.Error.CURRENCY_MISMATCH));
    }

    @Test
    void s12CountsNothingForADeselectedOutOfStockRow() {
        BasketPrice price = aCalculation()
                .calculate(aBasketOf(theFirstItem(), deselected(theSecondItemOutOfStock())), Channel.standard);

        assertThat(price).isEqualTo(aPrice(99890, 0, DELIVERY_RATE, 109880));
    }

    @Test
    void multipliesTheDiscountByTheQuantity() {
        BasketPrice price = aCalculation()
                .calculate(aBasketOf(twice(theFirstItemWithCatalogDiscount()), theSecondItem()), Channel.standard);

        assertThat(price).isEqualTo(aPrice(244770, 20000, DELIVERY_RATE, 254760));
    }

    @Test
    void chargesSmartDeliveryWhenTheConfigurationDoesNotFreeIt() {
        BasketPrice price = aCalculationThatChargesSmart()
                .calculate(aBasketOf(theFirstItem(), theSecondItem()), Channel.smart);

        assertThat(price).isEqualTo(aPrice(164880, 0, DELIVERY_RATE, 174870));
    }

    @Test
    void pricesInTheCurrencyTheBasketHolds() {
        BasketPrice price = aCalculation()
                .calculate(aBasketOfIn(EUR, anItemPricedIn(BasketFixture.FIRST_OFFER, EUR)), Channel.standard);

        assertThat(price).isEqualTo(aPriceIn(EUR, 99890, 0, DELIVERY_RATE, 109880));
    }
}
