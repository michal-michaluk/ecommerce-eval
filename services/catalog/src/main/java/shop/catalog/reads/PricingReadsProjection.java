package shop.catalog.reads;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.context.event.EventListener;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import shop.catalog.pricing.PricingChanged;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * The pricing read model of 16, built from the pricing context's published payload and answered per currency.
 *
 * <p>Its edge is {@link PricingChanged}, which carries the whole series and every discount of one offer because the pricing
 * snapshot cannot leave that context: its components are its own package-private values and it names no offer. The handler copies
 * both into this context's own table, in this context's own vocabulary, so the pricing view reads one row and never a producer's
 * model ({@code projection.copy-of-foreign-payload}, {@code contract.own-value-objects}).
 *
 * <p>Which entries are current, scheduled or history, and what the offer costs on a date the reader picks, are answered when the
 * screen is served: both depend on the clock, and 10's calculation is asked through {@link EffectivePrices} rather than restated
 * here (RULE-4, 17 RULE-6).
 */
@Component
@Transactional
@AllArgsConstructor
class PricingReadsProjection {

    private final PricingRows rows;
    private final EffectivePrices effectivePrices;
    private final Clock clock;

    @EventListener
    public void handle(PricingChanged changed) {
        PricingRow row = rows.findById(changed.offerId()).orElseGet(() -> new PricingRow(changed.offerId()));
        rows.save(row.copyOf(new PriceSeries(changed.prices().stream().map(PricedItem::of).toList()),
                new DiscountSeries(changed.discounts().stream().map(DiscountItem::of).toList())));
    }

    /** The pricing screen of one offer in one currency, split by the clock and previewed for {@code previewAt}. */
    @Transactional(readOnly = true)
    public Optional<PricingReads> findPricing(String offerId, String currency, Instant previewAt) {
        return rows.findById(offerId).map(row -> PricingReads.ofNullable(row, currency, previewAt, Instant.now(clock),
                effectivePrices.effectivePriceOf(offerId, currency, previewAt)));
    }

    /** The stored price series of one offer: one wrapper, because a JSONB column needs one concrete type. */
    record PriceSeries(List<PricedItem> items) {

        PriceSeries {
            items = List.copyOf(items);
        }
    }

    /** The stored discounts of one offer. */
    record DiscountSeries(List<DiscountItem> items) {

        DiscountSeries {
            items = List.copyOf(items);
        }
    }

    interface PricingRows extends JpaRepository<PricingRow, String> {
    }

    @Entity
    @Table(name = "pricing_reads")
    static class PricingRow {

        @Id
        @Column(name = "offer_id")
        private String offerId;
        @Version
        private long version;
        @JdbcTypeCode(SqlTypes.JSON)
        private PriceSeries prices;
        @JdbcTypeCode(SqlTypes.JSON)
        private DiscountSeries discounts;

        protected PricingRow() {
        }

        PricingRow(String offerId) {
            this.offerId = offerId;
        }

        String offerId() {
            return offerId;
        }

        PriceSeries prices() {
            return prices == null ? new PriceSeries(List.of()) : prices;
        }

        DiscountSeries discounts() {
            return discounts == null ? new DiscountSeries(List.of()) : discounts;
        }

        PricingRow copyOf(PriceSeries prices, DiscountSeries discounts) {
            this.prices = prices;
            this.discounts = discounts;
            return this;
        }
    }
}
