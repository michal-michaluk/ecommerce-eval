package shop.shopping.basket;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import shop.shopping.kernel.Money;

/**
 * The basket's own configuration, in one place and one style for the {@code basket} prefix.
 *
 * <p>The currency an empty basket is opened in. Nothing in the specification says what a basket with no item is priced in,
 * while the API reports a currency on exactly that basket (element 07, {@code basket} step 2), so the deployment states it —
 * {@code SHOPPING_BASKET_CURRENCY} in the cluster, {@code basket.currency} in a profile — and {@link BasketCurrency} refuses a
 * code ISO-4217 does not know, so a misconfigured deployment fails while its bean is created instead of on the first basket.
 *
 * <p>The delivery estimate's block, {@code basket.estimate.*}, binds into the same {@code basket} prefix: one prefix, one
 * properties record, one place a reader looks (element 05 §Configuration).
 *
 * <p>The two pure policies are beans here and nowhere else: element 04's calculation and element 05's estimate read
 * configuration and a caller's instant and hold no state, so this class is the only place that can hand them what they read —
 * and without the beans neither a render nor the handover could price or date anything.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({BasketConfiguration.Settings.class, BasketConfiguration.OfferFixture.class})
class BasketConfiguration {

    @Bean
    BasketCurrency basketCurrency(Settings settings) {
        return new BasketCurrency(settings.currency());
    }

    /**
     * Element 08's port as this increment has it (D38): the fixture adapter stands in for a provider that publishes no
     * shop-facing offer read yet, and the browsing slice's adapter replaces it without this wiring changing.
     *
     * <p>It is a bean because the render route is the port's first caller (D60): without it neither the pull on read nor an add
     * could answer anything, and the service would not start with a dependency nobody provides.
     *
     * <p>The seed is the deployment's (D67): {@code basket.offer-fixture.offers} is what the cluster states, so a fixture with
     * no key is an empty catalog — the shape every test and every deployment that states no seed keeps — and a stated one is
     * converted here, at bean creation, so a malformed seed fails the pod instead of answering a wrong row on step 3.
     *
     * @param clock   the clock the adapter stamps its reads with
     * @param fixture the {@code basket.offer-fixture.*} block, absent when the deployment seeds nothing
     * @return the catalog the basket reads its offers through
     */
    @Bean
    OfferCatalog offerCatalog(Clock clock, OfferFixture fixture) {
        return new FixtureOfferCatalog(clock, offeringsOf(fixture));
    }

    /**
     * Element 04's calculation, charging the delivery the deployment states.
     *
     * <p>An absent {@code basket.delivery} block, or one that states only half of it, answers the two values the capture fixes
     * ({@code 9,99 zł} a shipment, free on SMART) rather than {@code 0,00} for everything: a rate nobody stated is a stated
     * default of the specification, and a silent zero would give delivery away for free.
     *
     * @param settings the {@code basket.*} block
     * @return the calculation the view and the handover price with
     */
    @Bean
    BasketPriceCalculation basketPriceCalculation(Settings settings) {
        return new BasketPriceCalculation(deliveryRatesOf(settings));
    }

    /**
     * Element 05's estimate, reading {@code basket.estimate.*}.
     *
     * <p>The block is passed as it binds, {@code null} included: element 05's I5 requires an absent key to answer
     * {@code unknown}, so no default is applied on the way in (D58).
     *
     * @param settings the {@code basket.*} block
     * @return the policy the handover dates the delivery with
     */
    @Bean
    BasketDeliveryEstimate basketDeliveryEstimate(Settings settings) {
        return new BasketDeliveryEstimate(settings.estimate());
    }

    /**
     * The rate the deployment states, key by key, each absent one answered by the stated default of element 04.
     *
     * <p>Both components are boxed so that "the deployment stated no rate" is observable here instead of arriving as {@code 0}:
     * a block that names only {@code free-when-smart: false} would otherwise price every shipment at {@code 0,00}, and one that
     * names only a rate would silently free a SMART delivery it never meant to free. The default is applied per key, never as a
     * pair, precisely so that a half-stated block cannot flip the other half.
     *
     * @param settings the {@code basket.*} block
     * @return what one shipment costs on the given channel
     */
    static DeliveryRates deliveryRatesOf(Settings settings) {
        Settings.DeliverySettings delivery = settings.delivery();
        DeliveryRates stated = DeliveryRates.defaultRates();
        if (delivery == null) {
            return stated;
        }
        return new DeliveryRates(
                delivery.rate() == null ? stated.ratePerShipment() : delivery.rate(),
                delivery.freeWhenSmart() == null ? stated.freeWhenSmart() : delivery.freeWhenSmart());
    }

    /**
     * The offers the deployment seeds into element 08's fixture adapter (D67), read as configuration and never compiled in:
     * the contract's offers are the browsing slice's data, and this increment states them beside the currency and the delivery
     * rate it states the same way.
     *
     * <p>One entry per offer, every field a read answers. {@code answers} is the warehouse's answer per read of that offer,
     * in order, the last one repeating — the queue is per offer because the contract turns one row out of stock while its
     * neighbours stay on sale. A seed without {@code answers} is an offer whose stock nobody read (A5).
     *
     * @param offers the seeded offers, absent when the deployment seeds none
     */
    @ConfigurationProperties(prefix = "basket.offer-fixture")
    record OfferFixture(List<SeededOffer> offers) {

        /**
         * One seeded offer, as the deployment states it.
         *
         * @param id           the offer the provider knows, the identity every read is matched on
         * @param seller       the seller the row is grouped into as a shipment (02 RULE-7)
         * @param title        the product title, one line in the row
         * @param picture      the one picture the row shows, a URL
         * @param listPrice    the catalog's list price in fine units, which no row may hold (D53)
         * @param currentPrice the price the offer is sold at, in fine units, flattened by the provider (D22)
         * @param discount     the discount the provider flattened, in fine units, {@code 0} when it names none (D22)
         * @param currency     the ISO-4217 code every amount of this offer is in
         * @param state        {@code onSale} or {@code removed}; absent reads as {@code onSale}
         * @param answers      the warehouse answers in order of this offer's reads, absent for never read
         */
        record SeededOffer(String id, String seller, String title, String picture, Long listPrice,
                Long currentPrice, Long discount, String currency, OfferObservation.OfferState state,
                List<StockAnswer> answers) {
        }

        /**
         * One entry of an offer's queue: the stock this read answers and how many reads it holds for.
         *
         * @param stock {@code notRead} or a count of units, which may be {@code 0} or below and is never repaired (D56)
         * @param reads how many consecutive reads answer it, {@code 1} when absent
         */
        record StockAnswer(String stock, Integer reads) {
        }
    }

    /**
     * The seeded offers as element 08's adapter takes them, field by field, refusing a seed that cannot answer a row.
     *
     * <p>Every refusal happens at bean creation, never on the first read: the seed is deployment configuration, never a
     * client's request, so a missing title or an unknown currency must stop the pod the way a bad {@code basket.currency}
     * does — an offer that answers {@code 200} and describes nothing would surface as {@code OFFER_READ_UNAVAILABLE} on the
     * first add (element 07 §Error contract), which is the wrong complaint about the wrong file. An absent field is an
     * {@link IllegalArgumentException}; the currency is normalised and refused by {@link BasketCurrency}, the one type that
     * already owns that judgement.
     *
     * @param fixture the {@code basket.offer-fixture.*} block, absent when the deployment seeds nothing
     * @return one offering per seeded offer, in the order the deployment states them
     */
    static List<FixtureOfferCatalog.Offering> offeringsOf(OfferFixture fixture) {
        if (fixture == null || fixture.offers() == null) {
            return List.of();
        }
        return fixture.offers().stream().map(BasketConfiguration::offeringOf).toList();
    }

    private static FixtureOfferCatalog.Offering offeringOf(OfferFixture.SeededOffer seed) {
        String offerId = stated(seed.id(), null, "id");
        return new FixtureOfferCatalog.Offering(offerId, descriptionOf(offerId, seed),
                seed.state() == null ? OfferObservation.OfferState.onSale : seed.state(),
                stockAnswersOf(offerId, seed.answers()));
    }

    private static OfferRecord.OfferDescription descriptionOf(String offerId, OfferFixture.SeededOffer seed) {
        String currency = new BasketCurrency(stated(seed.currency(), offerId, "currency")).code();
        return new OfferRecord.OfferDescription(stated(seed.seller(), offerId, "seller"),
                stated(seed.title(), offerId, "title"), stated(seed.picture(), offerId, "picture"),
                new Money(stated(seed.listPrice(), offerId, "list-price"), currency),
                new Money(stated(seed.currentPrice(), offerId, "current-price"), currency),
                new Money(stated(seed.discount(), offerId, "discount"), currency, Money.Kind.discount));
    }

    private static List<OfferObservation.Stock> stockAnswersOf(String offerId, List<OfferFixture.StockAnswer> answers) {
        if (answers == null || answers.isEmpty()) {
            return List.of(OfferObservation.Stock.NotRead.notRead);
        }
        List<OfferObservation.Stock> queue = new ArrayList<>();
        for (OfferFixture.StockAnswer answer : answers) {
            queue.addAll(Collections.nCopies(readsOf(offerId, answer.reads()), stockOf(offerId, answer.stock())));
        }
        return List.copyOf(queue);
    }

    private static int readsOf(String offerId, Integer reads) {
        int stated = reads == null ? 1 : reads;
        if (stated < 1) {
            throw new IllegalArgumentException(
                    "basket.offer-fixture of " + offerId + " states " + reads + " reads, which is no answer");
        }
        return stated;
    }

    private static OfferObservation.Stock stockOf(String offerId, String stock) {
        String answer = stated(stock, offerId, "answers[].stock").trim();
        if (answer.equalsIgnoreCase(OfferObservation.Stock.NotRead.notRead.name())) {
            return OfferObservation.Stock.NotRead.notRead;
        }
        try {
            return new OfferObservation.Stock.Quantity(Integer.parseInt(answer));
        } catch (NumberFormatException notACount) {
            throw new IllegalArgumentException("basket.offer-fixture of " + offerId + " states stock '" + stock
                    + "', which is neither notRead nor a count of units", notACount);
        }
    }

    private static String stated(String value, String offerId, String key) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("basket.offer-fixture" + (offerId == null ? "" : " of " + offerId)
                    + " states no " + key);
        }
        return value;
    }

    private static long stated(Long value, String offerId, String key) {
        if (value == null) {
            throw new IllegalArgumentException("basket.offer-fixture of " + offerId + " states no " + key);
        }
        return value;
    }

    /**
     * The {@code basket.*} block. It stays a plain record: the model carries no framework annotation, so the binding lives
     * on this wiring class and the values arrive already typed.
     *
     * @param currency the ISO-4217 code a new basket is opened in
     * @param estimate the {@code basket.estimate.*} block {@link BasketDeliveryEstimate} reads, absent when the deployment
     *                 states none
     * @param delivery the {@code basket.delivery.*} block {@link BasketPriceCalculation} charges, absent when the deployment
     *                 states none
     */
    @ConfigurationProperties(prefix = "basket")
    record Settings(String currency, EstimateSettings estimate, DeliverySettings delivery) {

        /**
         * {@code basket.delivery.*} — what one seller shipment costs and whether the SMART channel frees it (element 04
         * §Parameters, {@code basket.delivery.rate}, {@code basket.delivery.freeWhenSmart}).
         *
         * <p>No {@code @DefaultValue} sits on either component, and both are boxed: an absent key stays <em>absent</em> and is
         * answered by {@link DeliveryRates#defaultRates()} in the wiring, so a half-stated block is a stated default rather than
         * a rate of {@code 0,00}.
         *
         * @param rate          what one seller shipment costs, in fine units of the basket's currency
         * @param freeWhenSmart whether the SMART channel frees delivery
         */
        record DeliverySettings(Long rate, Boolean freeWhenSmart) {
        }

        /**
         * {@code basket.estimate.*} — the three keys element 05 fixes (D36, D43), bound exactly where {@code basket.currency}
         * binds.
         *
         * <p>No {@code @DefaultValue} sits on any component, and that is deliberate: a default filled in here would make an
         * absent key look like a stated one, while element 05's I5 requires an absent key to answer the fallback instead. The
         * block is data — {@link BasketDeliveryEstimate} is the only reader, it decides what an absent or unusable value
         * means, and it answers {@link Estimate#unknown} rather than an exception.
         *
         * @param defaultDays the range in calendar days from the customer's today (D36)
         * @param zone        the zone whose today the range counts from (D43)
         * @param fallback    what to answer when the configuration cannot produce a range; {@code unknown} is its only
         *                    specified value (D7)
         */
        record EstimateSettings(DefaultDays defaultDays, String zone, String fallback) {

            /**
             * {@code basket.estimate.defaultDays} — the two ends of the estimate, in calendar days from today.
             *
             * @param from days from today to the first day of the range, inclusive
             * @param to   days from today to the last day of the range, inclusive
             */
            record DefaultDays(int from, int to) {
            }
        }
    }
}
