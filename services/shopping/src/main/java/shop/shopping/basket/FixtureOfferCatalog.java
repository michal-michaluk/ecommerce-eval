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
 * means, so the fixture never has to invent an offer to stay truthful. A deployment states its seed as configuration instead
 * (D67): {@link Offering} carries every field a read answers — seller, title, picture, list price, current price, discount,
 * currency, {@code offerState} — and the stock answers in the order the reads of that offer receive them, the last one
 * repeating. That queue is per offer, because the contract's own sequence turns one row out of stock while the others stay
 * on sale. {@link #failingWith} injects the whole-read faults of element 08 §Failure mapping; the read records every call it
 * receives, so a test can prove what was asked of the provider and that nothing else — a reservation above all — was (DoD17).
 *
 * <p>The two malformed answers are answered as the provider would answer them, never repaired here: a stock count below zero
 * travels through untouched (D56) and a {@code 404} for the whole batch answers nothing at all, because the call was wrong, not
 * the offer. There is no reservation method, no state a reservation could hold, and no way to add one without widening the
 * port the ArchUnit rule forbids (R6).
 *
 * <p>A seeded offer may also answer as though the provider refused the call ({@link Failure#serverError}), which is the one fault
 * element 06 A4 speaks of and the one {@link #failingWith} injects: that is how a deployment states an unreadable offer, so the
 * {@code stale} half of a rendered row can be observed over HTTP and not only in a test (DoD18).
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

    /**
     * The fixture a deployment states: the offers configuration seeds, each one answered as its own seed says.
     *
     * @param clock     the clock the adapter stamps its reads with
     * @param offerings the seeded offers, none of them twice
     */
    FixtureOfferCatalog(Clock clock, List<Offering> offerings) {
        this(clock);
        offerings.forEach(this::seed);
    }

    @Override
    public List<OfferRecord> read(List<String> offerIds) {
        Objects.requireNonNull(offerIds, "offerIds");
        if (offerIds.isEmpty()) {
            offers.values().forEach(Seeded::rewind);
            return List.of();
        }
        reads.add(List.copyOf(offerIds));
        return switch (failureOf(offerIds)) {
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
                List.of(new OfferObservation.Stock.Quantity(units)), Failure.none, 0));
    }

    /**
     * Seeds an offer that is on sale while the warehouse did not answer — the row stays orderable and marked stale (A5).
     *
     * @param offerId the offer the provider knows
     */
    void sellsWithoutStockRead(String offerId) {
        offers.put(offerId, new Seeded(aDescription(offerId), OfferObservation.OfferState.onSale,
                List.of(OfferObservation.Stock.NotRead.notRead), Failure.none, 0));
    }

    /**
     * Seeds an offer the provider answered about and no longer offers — the row stays, greyed and blocking (A3, D33).
     *
     * @param offerId the offer the provider withdrew
     */
    void removed(String offerId) {
        offers.put(offerId, new Seeded(null, OfferObservation.OfferState.removed,
                List.of(OfferObservation.Stock.NotRead.notRead), Failure.none, 0));
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
     * The provider's answer for this read: the fault the asked-for offer was seeded with, or the one {@link #failingWith}
     * injected for the whole adapter. An injected outage outranks a seed, because a test that states one is stating what the
     * whole provider does.
     *
     * @param offerIds the ids this read asked for
     * @return the fault to answer with, {@link Failure#none} when every asked-for offer is healthy
     */
    private Failure failureOf(List<String> offerIds) {
        if (failure != Failure.none) {
            return failure;
        }
        return offerIds.stream()
                .map(offers::get)
                .filter(Objects::nonNull)
                .map(Seeded::faultNow)
                .filter(seededFault -> seededFault != Failure.none)
                .findFirst()
                .orElse(Failure.none);
    }

    /**
     * Every call this adapter received, in order, each one the ids it was asked for — the ledger DoD17 reads.
     *
     * @return one entry per read, immutable
     */
    List<List<String>> reads() {
        return List.copyOf(reads);
    }

    private void seed(Offering offering) {
        Seeded seeded = new Seeded(offering.description(), offering.offerState(), offering.stockAnswers(),
                offering.fault(), offering.faultAfter());
        if (offers.put(offering.offerId(), seeded) != null) {
            throw new IllegalArgumentException("the fixture seeds one offer twice, got " + offering.offerId());
        }
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

    /**
     * One offer as a deployment seeds it — every field a read answers about the offer itself, and the warehouse answers in the
     * order this offer's reads receive them.
     *
     * <p>The queue is what makes a row's availability move inside the contract's own sequence: the third read of an offer may
     * answer no units left while the row is on sale, and the last answer repeats, because an offer that runs out stays out.
     * A seed here is data, never a rule — a {@code null} description is the id a {@code 200} left out (A3) and stays
     * {@code OFFER_NOT_FOUND} for an add, while a described removal greys a row with the price the provider still names.
     *
     * @param offerId      the identity the seed states
     * @param description  what the provider says about the offer, absent when it says nothing about the content
     * @param offerState   whether the provider offers the thing or has withdrawn it
     * @param stockAnswers the warehouse answers in order of the reads of this offer, at least one, the last repeating
     * @param fault        what the provider does when this offer is asked about, {@link Failure#none} when it answers
     * @param faultAfter   how many reads of this offer the provider answers before {@code fault} takes hold, {@code 0} for
     *                     "from the first read on"
     */
    record Offering(String offerId, OfferRecord.OfferDescription description,
            OfferObservation.OfferState offerState, List<OfferObservation.Stock> stockAnswers, Failure fault,
            int faultAfter) {

        Offering {
            Objects.requireNonNull(offerId, "offerId");
            Objects.requireNonNull(offerState, "offerState");
            Objects.requireNonNull(fault, "fault");
            if (faultAfter < 0) {
                throw new IllegalArgumentException("an offer cannot fault before its first read, got " + faultAfter);
            }
            if (stockAnswers == null || stockAnswers.isEmpty()) {
                throw new IllegalArgumentException("an offer answers at least one read, got none for " + offerId);
            }
        }
    }

    /** What the provider knows about one offer, without the instant — the read stamps that when it answers. */
    private static final class Seeded {

        private final OfferRecord.OfferDescription description;
        private final OfferObservation.OfferState offerState;
        private final List<OfferObservation.Stock> stockAnswers;
        private final Failure fault;
        private final int faultAfter;
        private int read;
        private int answered;

        private Seeded(OfferRecord.OfferDescription description, OfferObservation.OfferState offerState,
                List<OfferObservation.Stock> stockAnswers, Failure fault, int faultAfter) {
            this.description = description;
            this.offerState = offerState;
            this.stockAnswers = List.copyOf(stockAnswers);
            this.fault = fault;
            this.faultAfter = faultAfter;
        }

        /**
         * The fault this read runs into: none while the provider still owes this offer an answer, the seeded fault from the
         * read the seed names on. Pure — the counter moves only when a read is actually answered.
         *
         * @return the fault to answer with
         */
        private Failure faultNow() {
            return answered < faultAfter ? Failure.none : fault;
        }

        private OfferRecord asRecord(String offerId, Instant readAt) {
            return new OfferRecord(offerId, description, offerState, next(), readAt);
        }

        private OfferObservation.Stock next() {
            OfferObservation.Stock stock = stockAnswers.get(Math.min(read, stockAnswers.size() - 1));
            read = Math.min(read + 1, stockAnswers.size());
            answered++;
            return stock;
        }

        private void rewind() {
            read = 0;
            answered = 0;
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
