package shop.catalog.pricing;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.RequiredArgsConstructor;
import org.hibernate.annotations.DynamicUpdate;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Primary;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The normalized strategy of {@link PricingRepository} (persistence-adapters.md): one row per price entry and per
 * discount, so the history is the table — a change closes the predecessor's {@code valid_until} and appends a row,
 * never overwrites one (03 RULE-2). {@code activated} is the RULE-9 marker: it is written in the same transaction as
 * the {@code *Activated} fact, which is exactly what makes activation idempotent and recoverable after downtime.
 *
 * <p>{@code get} rehydrates through the aggregate's all-args constructor with the rows in {@code valid_from} order;
 * {@code save} drains the event buffer before writing, maps the snapshot onto the rows, and only then publishes the
 * snapshot once, the {@code reads} payload once and every event once — a save that emitted nothing publishes nothing.
 */
@Primary
@Repository
@RequiredArgsConstructor
class NormalizingPricingRepository implements PricingRepository {

    private final PricingModels models;
    private final PriceEntries priceEntries;
    private final Discounts discounts;
    private final ApplicationEventPublisher publisher;
    private final Clock clock;

    @Override
    public Optional<PricingModelEditor> get(String offerId) {
        return models.findById(offerId).map(entity -> rehydrated(entity.offerId));
    }

    @Override
    public void save(PricingModelEditor pricing) {
        List<PricingEvent> emitted = eventsEmittedFrom(pricing);
        Instant now = Instant.now(clock);
        PricingModel model = pricing.toPricingModel(now);

        models.save(modelRow(pricing.offerId, now, !emitted.isEmpty()));
        sync(pricing.offerId, model);

        if (!emitted.isEmpty()) {
            publisher.publishEvent(model);
            publisher.publishEvent(PricingChanged.of(pricing.offerId, model));
        }
        emitted.forEach(publisher::publishEvent);
    }

    private void sync(String offerId, PricingModel model) {
        syncPriceEntries(offerId, pricesOf(model));
        syncDiscounts(offerId, model.discounts());
    }

    /**
     * The offers the RULE-9 poll must look at: those still carrying an entry without its marker, whatever that entry's
     * {@code validFrom}. Which of them is due is {@link ActivationDue}'s decision, not this query's.
     */
    List<String> offerIdsAwaitingActivation() {
        Stream<String> priced = priceEntries.findByActivatedFalse().stream().map(entity -> entity.offerId);
        Stream<String> discounted = discounts.findByActivatedFalse().stream().map(entity -> entity.offerId);
        return Stream.concat(priced, discounted).distinct().toList();
    }

    /** Copy the buffer and clear it before writing, so a failed save cannot publish the same fact twice. */
    static List<PricingEvent> eventsEmittedFrom(PricingModelEditor pricing) {
        List<PricingEvent> emitted = List.copyOf(pricing.events);
        pricing.events.clear();
        return emitted;
    }

    private PricingModelEditor rehydrated(String offerId) {
        return new PricingModelEditor(offerId, new ArrayList<>(),
                priceEntries.findByOfferIdOrderByValidFrom(offerId).stream()
                        .map(NormalizingPricingRepository::priceEntry).toList(),
                discounts.findByOfferIdOrderByValidFrom(offerId).stream()
                        .map(NormalizingPricingRepository::discount).toList());
    }

    private PricingModelEntity modelRow(String offerId, Instant now, boolean changed) {
        PricingModelEntity row = models.findById(offerId).orElseGet(() -> new PricingModelEntity(offerId, now));
        return changed ? row.markChanged(now) : row;
    }

    private void syncPriceEntries(String offerId, List<PriceEntry> prices) {
        Map<String, PriceEntryEntity> rows = priceEntries.findByOfferId(offerId).stream()
                .collect(Collectors.toMap(row -> row.priceId, row -> row));
        prices.forEach(entry -> priceEntries.save(priceRow(rows.remove(entry.priceId().value()), offerId, entry)));
        rows.values().forEach(priceEntries::delete);
    }

    private void syncDiscounts(String offerId, List<Discount> stored) {
        Map<String, DiscountEntity> rows = discounts.findByOfferId(offerId).stream()
                .collect(Collectors.toMap(row -> row.discountId, row -> row));
        stored.forEach(discount -> discounts.save(discountRow(rows.remove(discount.discountId().value()),
                offerId, discount)));
        rows.values().forEach(discounts::delete);
    }

    private static List<PriceEntry> pricesOf(PricingModel model) {
        return Stream.of(model.current(), model.scheduled(), model.history()).flatMap(List::stream).toList();
    }

    private static PriceEntryEntity priceRow(PriceEntryEntity row, String offerId, PriceEntry entry) {
        PriceEntryEntity stored = row == null ? new PriceEntryEntity() : row;
        stored.offerId = offerId;
        stored.priceId = entry.priceId().value();
        stored.amountMinor = entry.price().amountMinor();
        stored.currency = entry.price().currency();
        stored.validFrom = entry.validity().validFrom();
        stored.validUntil = entry.validity().validUntil();
        stored.activated = entry.activated();
        return stored;
    }

    private static PriceEntry priceEntry(PriceEntryEntity row) {
        return new PriceEntry(new PriceId(row.priceId), Money.ofMinor(row.amountMinor, row.currency),
                new Validity(row.validFrom, row.validUntil), row.activated);
    }

    private static DiscountEntity discountRow(DiscountEntity row, String offerId, Discount discount) {
        DiscountEntity stored = row == null ? new DiscountEntity() : row;
        stored.offerId = offerId;
        stored.discountId = discount.discountId().value();
        stored.kind = storedKind(discount.kind());
        stored.validFrom = discount.validity().validFrom();
        stored.validUntil = discount.validity().validUntil();
        stored.activated = discount.activated();
        switch (discount.kind()) {
            case Discount.Kind.Percentage percentage -> {
                stored.basisPoints = percentage.basisPoints();
                stored.amountMinor = null;
                stored.currency = null;
            }
            case Discount.Kind.Fixed fixed -> {
                stored.basisPoints = null;
                stored.amountMinor = fixed.amount().amountMinor();
                stored.currency = fixed.amount().currency();
            }
        }
        return stored;
    }

    private static Discount discount(DiscountEntity row) {
        return new Discount(new DiscountId(row.discountId), kindOf(row),
                new Validity(row.validFrom, row.validUntil), row.activated);
    }

    private static StoredKind storedKind(Discount.Kind kind) {
        return switch (kind) {
            case Discount.Kind.Percentage ignored -> StoredKind.PERCENTAGE;
            case Discount.Kind.Fixed ignored -> StoredKind.FIXED;
        };
    }

    private static Discount.Kind kindOf(DiscountEntity row) {
        return row.kind == StoredKind.PERCENTAGE
                ? new Discount.Kind.Percentage(row.basisPoints)
                : new Discount.Kind.Fixed(Money.ofMinor(row.amountMinor, row.currency));
    }

    /** The two families of discount as storage names them; the domain keeps them as a closed set of records. */
    enum StoredKind {
        PERCENTAGE,
        FIXED
    }

    interface PricingModels extends JpaRepository<PricingModelEntity, String> {
    }

    interface PriceEntries extends JpaRepository<PriceEntryEntity, Long> {

        List<PriceEntryEntity> findByOfferId(String offerId);

        List<PriceEntryEntity> findByOfferIdOrderByValidFrom(String offerId);

        List<PriceEntryEntity> findByActivatedFalse();
    }

    interface Discounts extends JpaRepository<DiscountEntity, Long> {

        List<DiscountEntity> findByOfferId(String offerId);

        List<DiscountEntity> findByOfferIdOrderByValidFrom(String offerId);

        List<DiscountEntity> findByActivatedFalse();
    }

    @Entity
    @Table(name = "pricing_model")
    static class PricingModelEntity {

        @Id
        private String offerId;
        @Version
        private long version;
        private Instant changedAt;

        protected PricingModelEntity() {
        }

        PricingModelEntity(String offerId, Instant changedAt) {
            this.offerId = offerId;
            this.changedAt = changedAt;
        }

        /** A changed model touches its own row, which is what makes the optimistic lock on the aggregate real. */
        PricingModelEntity markChanged(Instant at) {
            this.changedAt = at;
            return this;
        }
    }

    @Entity
    @DynamicUpdate
    @Table(name = "price_entry")
    static class PriceEntryEntity {

        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;
        private String offerId;
        private String priceId;
        private int amountMinor;
        private String currency;
        private Instant validFrom;
        private Instant validUntil;
        private boolean activated;
    }

    @Entity
    @DynamicUpdate
    @Table(name = "discount")
    static class DiscountEntity {

        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;
        private String offerId;
        private String discountId;
        @Enumerated(EnumType.STRING)
        private StoredKind kind;
        private Integer basisPoints;
        private Integer amountMinor;
        private String currency;
        private Instant validFrom;
        private Instant validUntil;
        private boolean activated;
    }
}
