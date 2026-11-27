package shop.shopping.basket;

import java.util.Objects;

import shop.shopping.kernel.Money;

/**
 * What the customer pays for what is selected — element 04's whole answer: four amounts and nothing else.
 *
 * <p>{@code productValue} is already net of the catalog's discount (D22), so {@code discountValue} is <em>reported beside</em>
 * the total and never added to it: the view prints {@code Rabat −10,00 zł} from the one and {@code Razem z dostawą} from the
 * other, and adding the second again would count it twice (S7).
 *
 * <p>{@code totalWithDelivery} is {@code productValue + deliveryValue}; a basket with nothing selected answers {@code 0,00 zł}
 * on all four — an amount, never "unknown" (element 03 RULE-8, S9).
 *
 * @param productValue      the selected rows, each at its quantity
 * @param discountValue     the catalog discounts of the same rows, of kind {@code discount}, reported and not subtracted
 * @param deliveryValue     one rate per seller shipment with at least one selected row, {@code 0,00} on SMART
 * @param totalWithDelivery the product value plus the delivery value
 */
record BasketPrice(
        Money productValue,
        Money discountValue,
        Money deliveryValue,
        Money totalWithDelivery) {

    BasketPrice {
        Objects.requireNonNull(productValue, "productValue");
        Objects.requireNonNull(discountValue, "discountValue");
        Objects.requireNonNull(deliveryValue, "deliveryValue");
        Objects.requireNonNull(totalWithDelivery, "totalWithDelivery");
    }
}
