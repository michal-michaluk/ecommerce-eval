package shop.catalog.reads;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import shop.catalog.IntegrationTest;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static shop.catalog.reads.ReadsFixture.OFFER_ID;
import static shop.catalog.reads.ReadsFixture.PLN;

/**
 * The pricing read model of 16 against a real database: the handler copies the published series and discounts into its own row, a
 * re-delivered payload rewrites it, and the screen is derived for the currency and the date the caller asks about.
 *
 * <p>It also proves the {@code reads → pricing} edge end to end: the preview is 10's answer, asked through the port and answered by
 * the mediator over a stored pricing model, never a calculation restated here.
 */
@IntegrationTest
class PricingReadsProjectionTest {

    @Autowired
    PricingReadsProjection projection;
    @Autowired
    PricingReadsProjection.PricingRows rows;
    @Autowired
    JdbcTemplate jdbc;

    @BeforeEach
    void clearsTheReadModel() {
        rows.deleteAll();
    }

    @Test
    void keepsOneRowForTheSamePayloadDeliveredTwice() {
        Instant now = Instant.now();

        projection.handle(ReadsFixture.priceSeries(OFFER_ID, now));
        projection.handle(ReadsFixture.priceSeries(OFFER_ID, now));

        assertThat(rows.count()).isEqualTo(1);
        PricingReads pricing = projection.findPricing(OFFER_ID, PLN, now).orElseThrow();
        assertThat(pricing.offerId()).isEqualTo(OFFER_ID);
        assertThat(pricing.current()).extracting(PricedItem::priceId).containsExactly("P-1");
        assertThat(pricing.scheduled()).extracting(PricedItem::priceId).containsExactly("P-2");
        assertThat(pricing.history()).extracting(PricedItem::priceId).containsExactly("P-0");
        assertThat(pricing.discounts().active()).extracting(DiscountItem::discountId).containsExactly("D-1");
    }

    /** The currency is the reader's choice, so one stored row answers as many screens as the offer prices currencies (16 D2). */
    @Test
    void answersPerCurrencyFromOneRow() {
        Instant now = Instant.now();
        projection.handle(ReadsFixture.priceSeries(OFFER_ID, now));

        assertThat(projection.findPricing(OFFER_ID, ReadsFixture.EUR, now).orElseThrow().current())
                .extracting(PricedItem::priceId).containsExactly("P-9");
        assertThat(projection.findPricing(OFFER_ID, "JPY", now)).isEmpty();
    }

    @Test
    void answersEmptyForAnOfferThatWasNeverPriced() {
        assertThat(projection.findPricing("OFF-UNKNOWN", PLN, Instant.now())).isEmpty();
    }

    /**
     * 16 RULE-4 across the {@code reads → pricing} edge: the preview is 10's answer about a price the pricing context stores, asked
     * through {@link EffectivePrices} and answered by the mediator over {@code PricingService}. The entry is seeded here because the
     * pricing write commands take that context's own package-private records.
     */
    @Test
    void previewsWhatTheStoredPricingModelCostsOnTheChosenDate() {
        String offerId = "OFF-" + UUID.randomUUID();
        Instant now = Instant.now();
        givenPriceEntry(offerId, now.minusSeconds(86_400));
        projection.handle(ReadsFixture.priceSeries(offerId, now));

        PricingReads pricing = projection.findPricing(offerId, PLN, now).orElseThrow();

        assertThat(pricing.effectivePreview()).isNotNull();
        assertThat(pricing.effectivePreview().amountMinor()).isEqualTo(30989);
        assertThat(pricing.effectivePreview().currency()).isEqualTo(PLN);
        assertThat(pricing.effectivePreview().at()).isEqualTo(now);
    }

    private void givenPriceEntry(String offerId, Instant validFrom) {
        jdbc.update("insert into pricing_model (offer_id, changed_at, version) values (?, now(), 1)", offerId);
        jdbc.update("insert into price_entry (offer_id, price_id, amount_minor, currency, valid_from, activated) "
                + "values (?, 'P-1', 30989, 'PLN', ?, true)", offerId, Timestamp.from(validFrom));
    }
}
