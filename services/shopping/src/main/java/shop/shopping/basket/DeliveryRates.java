package shop.shopping.basket;

import java.util.Objects;

import shop.shopping.kernel.Money;

/**
 * What delivery costs — the configuration element 04's rate reads: one amount per seller shipment and the SMART rule
 * ({@code basket.delivery.rate}, {@code basket.delivery.freeWhenSmart}).
 *
 * <p>There is no threshold, because none exists (D23, Q20): a shipment costs the same whether the basket holds one unit or a
 * hundred, and the only thing that makes it free is the channel. The amount is in <em>fine units</em> and not bound to a
 * currency, because a basket holds exactly one currency and its delivery is charged in it (element 03 RULE-2, D50) — for PLN
 * the default {@code 9990} is the capture's {@code 9,99 zł}.
 *
 * <p>The defaults are the only values any material shows, so they are declared here, once, and the calculation reads them
 * through {@link #defaultRates()} instead of carrying a copy of its own.
 *
 * @param ratePerShipment what one seller shipment costs, in fine units of the basket's currency
 * @param freeWhenSmart   whether the SMART channel frees delivery ({@code 0,00 zł ze SMART}, element 04 §Variants)
 */
record DeliveryRates(long ratePerShipment, boolean freeWhenSmart) {

    /** The rate and the SMART rule of the capture — the only configuration any material states (element 04 §Parameters). */
    static DeliveryRates defaultRates() {
        return new DeliveryRates(9990, true);
    }

    /**
     * What one shipment costs on the given channel, in the basket's one currency.
     *
     * <p>The zero is an amount of this currency, not an absence (element 03 RULE-8): {@code 0,00 zł} is what the customer
     * sees next to {@code Dostawa} on a SMART basket.
     *
     * @param channel  how the customer arrives
     * @param currency the currency the basket holds
     * @return the rate of one shipment
     */
    Money rateFor(Channel channel, String currency) {
        Objects.requireNonNull(channel, "channel");
        return freeWhenSmart && channel == Channel.smart
                ? Money.zero(currency)
                : new Money(ratePerShipment, currency);
    }
}
