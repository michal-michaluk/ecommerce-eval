package shop.shopping.basket;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import shop.shopping.kernel.Money;

/**
 * Element 08's {@link OfferCatalog} as this increment has it (D38): the stand-in for a provider that publishes no shop-facing
 * offer read yet, and the fault injector every negative row of element 08 and of DoD17–DoD19 needs.
 *
 * <p>It is seeded by whoever constructs it: {@link #sells} states an offer the catalog offers and the warehouse has,
 * {@link #sellsWithoutStockRead} states one whose warehouse did not answer, {@link #removed} states one the provider no longer
 * has, and an id nobody seeded is answered as {@code removed} — which is exactly what a {@code 200} that leaves an id out
 * means, so the fixture never has to invent an offer to stay truthful. {@link #failingWith} injects the whole-read faults of
 * element 08 §Failure mapping; the read records every call it receives, so a test can prove what was asked of the provider and
 * that nothing else — a reservation above all — was (DoD17).
 *
 * <p>The two malformed answers are answered as the provider would answer them, never repaired here: a stock count below zero
 * travels through untouched (D56) and a {@code 404} for the whole batch answers nothing at all, because the call was wrong, not
 * the offer. There is no reservation method, no state a reservation could hold, and no way to add one without widening the
 * port the ArchUnit rule forbids (R6).
 */
final class FixtureOfferCatalog implements OfferCatalog {

    /** The seller the fixture provider names — every offer it seeds belongs to this one (02 RULE-7). */
    static final String SELLER = "FIXTURE_SELLER";
    private static final String CURRENCY = "PLN";
    private static final long LIST_PRICE = 109890;
    private static final long CURRENT_PRICE = 99890;
    private static final long DISCOUNT = 10000;

    private final Clock clock;
    private final Map<String, Seeded> offers = new LinkedHashMap<>();
    private final List<List<String>> reads = new ArrayList<>();
    private Failure failure = Failure.none;

    FixtureOfferCatalog(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public List<OfferRecord> read(List<String> offerIds) {
        Objects.requireNonNull(offerIds, "offerIds");
        if (offerIds.isEmpty()) {
            return List.of();
        }
        reads.add(List.copyOf(offerIds));
        return switch (failure) {
            case malformedRequest -> List.of();
            case unauthorized, forbidden, timeout, serverError -> offerIds.stream().map(this::unreadable).toList();
            case none -> offerIds.stream().map(this::answered).toList();
        };
    }

    /**
     * Seeds an offer the catalog offers and the warehouse holds {@code units} of — a negative count included, because the
     * warehouse's answer is passed through and never repaired (D56).
     *
     * @param offerId the offer the provider knows
     * @param units   the count the warehouse reports
     */
    void sells(String offerId, int units) {
        offers.put(offerId, new Seeded(aDescription(offerId), OfferObservation.OfferState.onSale,
                new OfferObservation.Stock.Quantity(units)));
    }

    /**
     * Seeds an offer that is on sale while the warehouse did not answer — the row stays orderable and marked stale (A5).
     *
     * @param offerId the offer the provider knows
     */
    void sellsWithoutStockRead(String offerId) {
        offers.put(offerId, new Seeded(aDescription(offerId), OfferObservation.OfferState.onSale,
                OfferObservation.Stock.NotRead.notRead));
    }

    /**
     * Seeds an offer the provider answered about and no longer offers — the row stays, greyed and blocking (A3, D33).
     *
     * @param offerId the offer the provider withdrew
     */
    void removed(String offerId) {
        offers.put(offerId, new Seeded(null, OfferObservation.OfferState.removed,
                OfferObservation.Stock.NotRead.notRead));
    }

    /**
     * Injects the provider's answer for the whole read, whichever ids are asked for (element 08 §Failure mapping).
     *
     * @param fault the fault to answer with, {@link Failure#none} to answer normally again
     */
    void failingWith(Failure fault) {
        this.failure = Objects.requireNonNull(fault, "fault");
    }

    /**
     * Every call this adapter received, in order, each one the ids it was asked for — the ledger DoD17 reads.
     *
     * @return one entry per read, immutable
     */
    List<List<String>> reads() {
        return List.copyOf(reads);
    }

    private OfferRecord answered(String offerId) {
        Seeded seeded = offers.get(offerId);
        return seeded == null
                ? recordOf(offerId, null, OfferObservation.OfferState.removed)
                : seeded.asRecord(offerId, Instant.now(clock));
    }

    private OfferRecord unreadable(String offerId) {
        return recordOf(offerId, null, OfferObservation.OfferState.notRead);
    }

    private OfferRecord recordOf(String offerId, OfferRecord.OfferDescription description,
            OfferObservation.OfferState offerState) {
        return new OfferRecord(offerId, description, offerState, OfferObservation.Stock.NotRead.notRead,
                Instant.now(clock));
    }

    private static OfferRecord.OfferDescription aDescription(String offerId) {
        return new OfferRecord.OfferDescription(SELLER, "Fixture offer " + offerId, "fixture-" + offerId,
                new Money(LIST_PRICE, CURRENCY), new Money(CURRENT_PRICE, CURRENCY),
                new Money(DISCOUNT, CURRENCY, Money.Kind.discount));
    }

    /** What the provider knows about one offer, without the instant — the read stamps that when it answers. */
    private record Seeded(OfferRecord.OfferDescription description, OfferObservation.OfferState offerState,
            OfferObservation.Stock stock) {

        OfferRecord asRecord(String offerId, Instant readAt) {
            return new OfferRecord(offerId, description, offerState, stock, readAt);
        }
    }

    /**
     * The provider outcomes element 08 §Failure mapping names, each one a different answer to the basket: the two credential
     * faults, a timeout and a server error all fail the read ({@code unavailable}, {@code stale}), while a malformed request
     * leaves the basket untouched because the call was wrong, not the offer.
     */
    enum Failure {
        none,
        unauthorized,
        forbidden,
        timeout,
        serverError,
        malformedRequest
    }
}
