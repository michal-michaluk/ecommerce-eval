package shop.catalog.reads;

import org.junit.jupiter.api.Test;
import shop.catalog.pricing.PricingChanged;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static shop.catalog.reads.ReadsFixture.EUR;
import static shop.catalog.reads.ReadsFixture.OFFER_ID;
import static shop.catalog.reads.ReadsFixture.PLN;
import static shop.catalog.reads.ReadsFixture.plnPriceSeries;

/**
 * The pricing view of 16 derived from one stored row: which entry is current, which starts later and which one a change closed, the
 * three discount lists, and 10's preview. Every expectation is stated against one instant, so no test depends on the day it runs.
 */
class PricingReadsTest {

    private static final Instant NOW = Instant.parse("2026-06-15T12:00:00Z");

    @Test
    void splitsTheSeriesIntoCurrentScheduledAndHistory() {
        PricingReads pricing = screen(PLN, NOW, Optional.empty());

        assertThat(pricing.current()).extracting(PricedItem::priceId).containsExactly("P-1");
        assertThat(pricing.scheduled()).extracting(PricedItem::priceId).containsExactly("P-2");
        assertThat(pricing.history()).extracting(PricedItem::priceId).containsExactly("P-0");
    }

    /** 16's order: the soonest scheduled entry first, the newest closed entry first. */
    @Test
    void ordersBothFutureAndPastEntriesByTheirOwnStart() {
        Instant now = NOW;
        PricingReadsProjection.PricingRow row = rowOf(ReadsFixture.series(OFFER_ID,
                List.of(
                        ReadsFixture.priceRow("P-2", 29900, PLN, now.plus(Duration.ofDays(60)), null),
                        ReadsFixture.priceRow("P-3", 27900, PLN, now.plus(Duration.ofDays(30)), null),
                        ReadsFixture.priceRow("P-0", 31900, PLN, now.minus(Duration.ofDays(120)), now.minus(Duration.ofDays(90))),
                        ReadsFixture.priceRow("P-9", 9900, PLN, now.minus(Duration.ofDays(200)), now.minus(Duration.ofDays(120)))),
                List.of()));

        PricingReads pricing = PricingReads.ofNullable(row, PLN, now, now, Optional.empty());

        assertThat(pricing).isNotNull();
        assertThat(pricing.scheduled()).extracting(PricedItem::priceId).containsExactly("P-3", "P-2");
        assertThat(pricing.history()).extracting(PricedItem::priceId).containsExactly("P-0", "P-9");
    }

    @Test
    void splitsDiscountsIntoActiveScheduledAndExpired() {
        PricingReads pricing = screen(PLN, NOW, Optional.empty());

        assertThat(pricing.discounts().active()).extracting(DiscountItem::discountId).containsExactly("D-1");
        assertThat(pricing.discounts().scheduled()).extracting(DiscountItem::discountId).containsExactly("D-2");
        assertThat(pricing.discounts().expired()).extracting(DiscountItem::discountId).containsExactly("D-0");
    }

    /** A fixed discount counts only in its own currency, a percentage in every currency (03 RULE-5). */
    @Test
    void keepsAFixedDiscountOutOfAForeignCurrencyAndAPercentageIn() {
        PricingReads pricing = PricingReads.ofNullable(
                rowOf(ReadsFixture.priceSeries(OFFER_ID, NOW)), EUR, NOW, NOW, Optional.empty());

        assertThat(pricing).isNotNull();
        assertThat(pricing.discounts().active()).extracting(DiscountItem::discountId).containsExactly("D-1");
        assertThat(pricing.discounts().scheduled()).isEmpty();
    }

    @Test
    void showsTensAnswerForTheDateTheReaderPickedAsThePreview() {
        PricingReads pricing = screen(PLN, NOW, Optional.of(new EffectivePrices.Result(26090, PLN)));

        assertThat(pricing.effectivePreview().at()).isEqualTo(NOW);
        assertThat(pricing.effectivePreview().amountMinor()).isEqualTo(26090);
        assertThat(pricing.effectivePreview().currency()).isEqualTo(PLN);
    }

    /** No open price means no preview at all: the amount {@code 0} is a real price and never a placeholder (13 RULE-7). */
    @Test
    void omitsThePreviewWhenNoPriceIsActive() {
        assertThat(screen(PLN, NOW, Optional.empty()).effectivePreview()).isNull();
    }

    /** A currency the offer never priced has no screen to render. */
    @Test
    void isAbsentForACurrencyTheOfferDoesNotPrice() {
        assertThat(PricingReads.ofNullable(rowOf(plnPriceSeries(OFFER_ID, NOW)), "JPY", NOW, NOW, Optional.empty())).isNull();
    }

    private static PricingReads screen(String currency, Instant now, Optional<EffectivePrices.Result> preview) {
        return PricingReads.ofNullable(rowOf(plnPriceSeries(OFFER_ID, now)), currency, now, now, preview);
    }

    private static PricingReadsProjection.PricingRow rowOf(PricingChanged series) {
        PricingReadsProjection.PricingRow row = new PricingReadsProjection.PricingRow(series.offerId());
        return row.copyOf(
                new PricingReadsProjection.PriceSeries(series.prices().stream().map(PricedItem::of).toList()),
                new PricingReadsProjection.DiscountSeries(series.discounts().stream().map(DiscountItem::of).toList()));
    }
}
