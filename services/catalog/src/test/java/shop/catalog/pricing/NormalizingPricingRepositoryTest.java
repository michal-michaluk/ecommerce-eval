package shop.catalog.pricing;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import shop.catalog.IntegrationTest;
import shop.catalog.RecordedEvents;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static shop.catalog.TestTransaction.transactional;
import static shop.catalog.pricing.ActivationFixture.priceDueInTheFuture;
import static shop.catalog.pricing.PricingAssert.assertRefused;
import static shop.catalog.pricing.PricingEventFixture.discountScheduled;
import static shop.catalog.pricing.PricingEventFixture.priceScheduled;
import static shop.catalog.pricing.PricingFixture.JANUARY_1;
import static shop.catalog.pricing.PricingFixture.JULY_1;
import static shop.catalog.pricing.PricingFixture.JULY_15;
import static shop.catalog.pricing.PricingFixture.JULY_8;
import static shop.catalog.pricing.PricingFixture.JUNE_1;
import static shop.catalog.pricing.PricingFixture.JUNE_15;
import static shop.catalog.pricing.PricingFixture.JUNE_20;
import static shop.catalog.pricing.PricingFixture.MAY_1;
import static shop.catalog.pricing.PricingFixture.OFFER_ID;
import static shop.catalog.pricing.PricingFixture.discountId;
import static shop.catalog.pricing.PricingFixture.openPriceChange;
import static shop.catalog.pricing.PricingFixture.pln;
import static shop.catalog.pricing.PricingFixture.priceId;
import static shop.catalog.pricing.PricingFixture.pricingModelOfInstanceA;
import static shop.catalog.pricing.PricingFixture.pricingModelWithOneOpenPrice;

/**
 * The normalized strategy against a real PostgreSQL: one row per price entry and per discount, the history kept by
 * rows, the RULE-9 marker and the adapter's event contract. Each write path is read back through a second transaction —
 * a first save INSERTs the rows, a command on the rehydrated model closes, reopens, re-marks or deletes an existing row,
 * and a third read proves what the store kept.
 *
 * <p>The integration profile recreates the schema from {@code db/changelog/0004-pricing.yaml} ({@code drop-first: true}),
 * so every test here also proves that the migration applies on a clean database.
 */
@IntegrationTest
@TestPropertySource(properties = "catalog.pricing.activation-interval=PT24H")
class NormalizingPricingRepositoryTest {

    @Autowired
    NormalizingPricingRepository repository;
    @Autowired
    RecordedEvents recorded;
    @Autowired
    JdbcTemplate jdbc;

    @Test
    void keepsEveryEntryAndDiscountOfInstanceA() {
        PricingModelEditor saved = pricingModelOfInstanceA();

        transactional(() -> repository.save(saved));

        Optional<PricingModelEditor> read = transactional(() -> repository.get(OFFER_ID));
        assertThat(read).isPresent();
        assertThat(read.orElseThrow().toPricingModel(JUNE_15)).isEqualTo(saved.toPricingModel(JUNE_15));
    }

    @Test
    void keepsTheHistoryAsRowsWithThePredecessorClosed() {
        transactional(() -> repository.save(pricingModelOfInstanceA()));

        PricingModelEditor read = transactional(() -> repository.get(OFFER_ID)).orElseThrow();

        PricingModelEditorAssert.assertThat(read).at(JULY_15)
                .hasCurrentPrice("P-2", 29900)
                .hasHistoricPriceClosedAt("P-1", JULY_1);
    }

    @Test
    void closesTheStoredPredecessorWhenTheSuccessorIsScheduled() {
        transactional(() -> repository.save(pricingModelWithOneOpenPrice()));
        PricingModelEditor stored = transactional(() -> repository.get(OFFER_ID)).orElseThrow();

        stored.schedulePrice(openPriceChange("P-2", pln(29900), JULY_1), JUNE_1);
        transactional(() -> repository.save(stored));

        PricingModel readModel = transactional(() -> repository.get(OFFER_ID)).orElseThrow().toPricingModel(JULY_15);
        assertThat(readModel.current()).extracting(PriceEntry::priceId).containsExactly(priceId("P-2"));
        PriceEntry predecessor = readModel.history().getFirst();
        assertThat(predecessor.priceId()).isEqualTo(priceId("P-1"));
        assertThat(predecessor.price().amountMinor()).isEqualTo(30989);
        assertThat(predecessor.validity().validUntil()).isEqualTo(JULY_1);
    }

    @Test
    void reopensTheStoredPredecessorWhenTheSuccessorIsRemoved() {
        transactional(() -> repository.save(pricingModelWithOneOpenPrice()));
        PricingModelEditor withSuccessor = transactional(() -> repository.get(OFFER_ID)).orElseThrow();
        withSuccessor.schedulePrice(openPriceChange("P-2", pln(29900), JULY_1), JUNE_1);
        transactional(() -> repository.save(withSuccessor));

        PricingModelEditor scheduled = transactional(() -> repository.get(OFFER_ID)).orElseThrow();
        scheduled.removePrice(priceId("P-2"), JUNE_1);
        transactional(() -> repository.save(scheduled));

        PricingModel readModel = transactional(() -> repository.get(OFFER_ID)).orElseThrow().toPricingModel(JULY_15);
        assertThat(readModel.current()).extracting(PriceEntry::priceId).containsExactly(priceId("P-1"));
        assertThat(readModel.current().getFirst().validity().isOpenEnded()).isTrue();
        assertThat(readModel.scheduled()).isEmpty();
    }

    @Test
    void dropsTheRowOfAStoredScheduledPrice() {
        transactional(() -> repository.save(priceDueInTheFuture()));
        PricingModelEditor stored = transactional(() -> repository.get(OFFER_ID)).orElseThrow();

        stored.removePrice(priceId("P-1"), JANUARY_1);
        transactional(() -> repository.save(stored));

        PricingModel kept = transactional(() -> repository.get(OFFER_ID)).orElseThrow().toPricingModel(JANUARY_1);
        assertThat(kept.scheduled()).isEmpty();
    }

    @Test
    void dropsTheRowOfAStoredScheduledDiscount() {
        transactional(() -> repository.save(pricingModelOfInstanceA()));
        PricingModelEditor stored = transactional(() -> repository.get(OFFER_ID)).orElseThrow();

        stored.removeDiscount(discountId("D-2"), JUNE_15);
        transactional(() -> repository.save(stored));

        PricingModel readModel = transactional(() -> repository.get(OFFER_ID)).orElseThrow().toPricingModel(JUNE_15);
        assertThat(readModel.discounts()).extracting(Discount::discountId).containsExactly(discountId("D-1"));
    }

    @Test
    void storesAMarkerThatWasAlreadyWrittenBeforeTheFirstSave() {
        PricingModelEditor saved = pricingModelOfInstanceA();
        saved.activateDueEntries(JUNE_20);
        transactional(() -> repository.save(saved));

        PricingModel readModel = transactional(() -> repository.get(OFFER_ID)).orElseThrow().toPricingModel(JUNE_20);

        assertThat(readModel.current()).extracting(PriceEntry::activated).containsExactly(true);
        assertThat(readModel.discounts()).extracting(Discount::activated).containsExactly(true, false);
    }

    @Test
    void updatesTheMarkerOfAStoredPriceAndDiscount() {
        transactional(() -> repository.save(pricingModelOfInstanceA()));
        PricingModelEditor stored = transactional(() -> repository.get(OFFER_ID)).orElseThrow();

        stored.activateDueEntries(JUNE_20);
        transactional(() -> repository.save(stored));

        PricingModel readModel = transactional(() -> repository.get(OFFER_ID)).orElseThrow().toPricingModel(JUNE_20);
        assertThat(readModel.current()).extracting(PriceEntry::activated).containsExactly(true);
        assertThat(readModel.discounts()).extracting(Discount::activated).containsExactly(true, false);
    }

    @Test
    void publishesTheSnapshotOnceAndEveryEventOnce() {
        transactional(() -> repository.save(pricingModelWithOneOpenPrice()));

        assertThat(recorded.of(PricingModel.class)).hasSize(1);
        assertThat(recorded.of(PricingChanged.class)).singleElement().satisfies(changed -> {
            assertThat(changed.offerId()).isEqualTo(OFFER_ID);
            assertThat(changed.prices()).extracting(PricingChanged.PriceRow::priceId).containsExactly("P-1");
            assertThat(changed.discounts()).isEmpty();
        });
        assertThat(recorded.of(PricingEvent.class))
                .containsExactly(priceScheduled("P-1", pln(30989), MAY_1, null));
    }

    @Test
    void publishesEveryEventOfAMultiEventSaveInOrder() {
        transactional(() -> repository.save(pricingModelOfInstanceA()));

        assertThat(recorded.of(PricingModel.class)).hasSize(1);
        assertThat(recorded.of(PricingEvent.class)).containsExactly(
                priceScheduled("P-1", pln(30989), MAY_1, JULY_1),
                priceScheduled("P-2", pln(29900), JULY_1, null),
                discountScheduled("D-1", new Discount.Kind.Percentage(1000), JUNE_20, JULY_1),
                discountScheduled("D-2", new Discount.Kind.Fixed(pln(2000)), JULY_1, JULY_8));
    }

    @Test
    void publishesTheSnapshotOfTheSaveThatActivatedAnEntry() {
        transactional(() -> repository.save(pricingModelOfInstanceA()));
        PricingModelEditor stored = transactional(() -> repository.get(OFFER_ID)).orElseThrow();
        stored.activateDueEntries(JUNE_20);

        transactional(() -> repository.save(stored));

        assertThat(recorded.of(PricingModel.class)).hasSize(2);
        assertThat(recorded.of(PricingEvent.PriceActivated.class)).hasSize(1);
        assertThat(recorded.of(PricingEvent.DiscountActivated.class)).hasSize(1);
    }

    @Test
    void publishesNothingForAModelThatEmittedNothing() {
        transactional(() -> repository.save(PricingModelEditor.newPricingModelFor(OFFER_ID)));

        assertThat(recorded.of(PricingModel.class)).isEmpty();
        assertThat(recorded.of(PricingEvent.class)).isEmpty();
    }

    @Test
    void bumpsTheVersionOfTheModelRowOnAChangedSaveAndKeepsItOnANoOpSave() {
        transactional(() -> repository.save(pricingModelWithOneOpenPrice()));
        Long afterTheFirstSave = versionOfTheModelRow();
        PricingModelEditor stored = transactional(() -> repository.get(OFFER_ID)).orElseThrow();

        transactional(() -> repository.save(stored));
        assertThat(versionOfTheModelRow()).isEqualTo(afterTheFirstSave);

        stored.schedulePrice(openPriceChange("P-2", pln(29900), JULY_1), JUNE_1);
        transactional(() -> repository.save(stored));
        assertThat(versionOfTheModelRow()).isGreaterThan(afterTheFirstSave);
    }

    @Test
    void refusesToRemoveAStartedPriceAfterTheRoundTrip() {
        transactional(() -> repository.save(pricingModelOfInstanceA()));
        PricingModelEditor read = transactional(() -> repository.get(OFFER_ID)).orElseThrow();

        assertRefused(PricingException.Error.PRICE_ALREADY_STARTED,
                () -> read.removePrice(priceId("P-1"), MAY_1));
    }

    @Test
    void refusesToRemoveAStartedDiscountAfterTheRoundTrip() {
        transactional(() -> repository.save(pricingModelOfInstanceA()));
        PricingModelEditor read = transactional(() -> repository.get(OFFER_ID)).orElseThrow();

        assertRefused(PricingException.Error.DISCOUNT_ALREADY_STARTED,
                () -> read.removeDiscount(discountId("D-1"), JUNE_20));
    }

    private Long versionOfTheModelRow() {
        return jdbc.queryForObject("select version from pricing_model where offer_id = ?", Long.class, OFFER_ID);
    }
}
