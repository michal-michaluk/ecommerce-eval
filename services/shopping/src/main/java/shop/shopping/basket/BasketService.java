package shop.shopping.basket;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

import shop.shopping.kernel.CheckoutHandovers;
import shop.shopping.kernel.Estimate;
import shop.shopping.kernel.Handover;
import shop.shopping.kernel.ShoppingException;

/**
 * The basket's primary port: one method per operation of element 02, each loading the aggregate by its identity, letting the
 * aggregate decide, and storing it back.
 *
 * <p>It holds no rule of its own — merge, quantity, blocking and handover identity belong to {@link BasketEditor}. What it
 * does own is the one write behind a read: a customer always has an active basket, so asking for it creates it when none
 * exists (RULE-1, D42, element 07 §Recorded deviations). Compiling a payload is the other thing it owns, because the totals and
 * the delivery estimate are policies over configuration that no aggregate may hold — the handover's (element 09 §Shape of
 * record) and the screen's {@link BasketContent} alike.
 *
 * <p>The render route is the third write behind a read, and the recorded deviation D62: {@code GET /baskets/{basketId}} reads
 * the basket's offers through {@link OfferCatalog}, applies each answer through the same ingestion path an event takes
 * (element 10 §Application), and stores before it computes — so two concurrent renders of one basket can lose the race and
 * answer {@code CONCURRENT_MODIFICATION}, which the client retries.
 *
 * <p>Creation is the one method with no ambient transaction: the attempt may be refused by the schema's partial unique
 * index, and the re-read of the winner needs a transaction of its own, because a refused statement leaves the surrounding
 * one unusable. With no transaction to inherit, each repository call is its own — and the loser of a creation race answers
 * the basket the winner stored, so the customer ends up with exactly one.
 */
@Service
@Transactional
class BasketService {

    private final BasketRepository baskets;
    private final BasketCurrency currency;
    private final BasketPriceCalculation calculation;
    private final ShipmentEstimates estimates;
    private final OfferCatalog offers;
    private final CheckoutHandovers checkouts;
    private final Clock clock;

    BasketService(BasketRepository baskets, BasketCurrency currency, BasketPriceCalculation calculation,
            BasketDeliveryEstimate deliveryEstimate, OfferCatalog offers, CheckoutHandovers checkouts, Clock clock) {
        this.baskets = baskets;
        this.currency = currency;
        this.calculation = calculation;
        this.estimates = ShipmentEstimates.answeredBy(deliveryEstimate);
        this.offers = offers;
        this.checkouts = checkouts;
        this.clock = clock;
    }

    /**
     * The customer's active basket, created when the customer has none (RULE-1, D42, Q27).
     *
     * @param customerId the identity the token carries
     * @return the basket as the read side serves it
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    Basket basketOf(CustomerId customerId) {
        Objects.requireNonNull(customerId, "customerId");
        return basketFor(customerId).toSnapshot();
    }

    /**
     * Reads one basket by its identity.
     *
     * @param basketId the identity the path carries
     * @return the basket as the read side serves it
     */
    @Transactional(readOnly = true)
    Basket basket(BasketId basketId) {
        return editor(basketId).toSnapshot();
    }

    /**
     * Element 07's render (D10, D62): the basket with its content, its totals and its estimate in one answer.
     *
     * <p>The pull happens before the read: the rows' offers are read in one batched call (element 08 §Port), each answer is
     * applied through the ingestion path, and the document is stored — which is the deviation D62 records, because a persisted
     * {@code readAt} makes that write happen on every render. A read that failed greys its row instead of failing the basket
     * (A4, D26), so the pull raises nothing here: only a lost optimistic lock leaves it as {@code CONCURRENT_MODIFICATION}.
     *
     * @param basketId the identity the path carries
     * @param channel  how the customer arrives, which decides whether delivery is charged
     * @return the payload of steps 2–22 of the contract
     */
    BasketContent render(BasketId basketId, Channel channel) {
        BasketEditor basket = editor(basketId);
        observed(basket);
        return content(store(basket), channel);
    }

    /**
     * Sets one row's absolute quantity, or adds the offer the basket does not hold yet (D28, F16, element 07 §Update
     * idempotency). A repeated command is the quantity it states, never a change relative to what the row held.
     *
     * <p>An offer the basket does not hold is read first, because a row cannot exist without the seller, the availability and
     * the last-known snapshot the catalog answers — and an offer nobody could read has no row to add, so it is refused with the
     * code element 07 gives that failure rather than stored as a guess (A4, D26).
     *
     * @param basketId the identity the path carries
     * @param offerId  the offer the path carries
     * @param quantity the absolute quantity, at least one
     * @param channel  how the customer arrives, which decides whether delivery is charged
     * @return the basket after the command
     */
    BasketContent setQuantity(BasketId basketId, String offerId, int quantity, Channel channel) {
        BasketEditor basket = editor(basketId);
        if (holds(basket, offerId)) {
            basket.changeQuantity(offerId, quantity);
        } else {
            basket.addToBasket(offered(offerId, quantity));
        }
        return content(store(basket), channel);
    }

    /**
     * Removes the rows one scope names (element 07 §Removal): {@code quantity: null} is what removes, and the scope decides
     * how many rows that is.
     *
     * @param basketId the identity the path carries
     * @param removal  which rows the command covers
     * @param offerId  the offer the body named, absent for the two scopes that name none
     * @param channel  how the customer arrives, which decides whether delivery is charged
     * @return the basket after the command
     */
    BasketContent remove(BasketId basketId, BasketRemoval removal, String offerId, Channel channel) {
        BasketEditor basket = editor(basketId);
        removal.removeFrom(basket, offerId);
        return content(store(basket), channel);
    }

    /**
     * Selects or deselects the rows one scope names, and answers the basket the new selection prices (VIEW-1, S2).
     *
     * @param basketId  the identity the path carries
     * @param selection which rows the command covers
     * @param selected  switch them on or off
     * @param channel   how the customer arrives, which decides whether delivery is charged
     * @return the basket after the command
     */
    BasketContent updateSelection(BasketId basketId, BasketSelection selection, boolean selected, Channel channel) {
        BasketEditor basket = editor(basketId);
        basket.select(selection, selected);
        return content(store(basket), channel);
    }

    Basket addToBasket(BasketId basketId, BasketItem item) {
        BasketEditor basket = editor(basketId);
        basket.addToBasket(item);
        return store(basket);
    }

    Basket changeQuantity(BasketId basketId, String offerId, int quantity) {
        BasketEditor basket = editor(basketId);
        basket.changeQuantity(offerId, quantity);
        return store(basket);
    }

    /**
     * Applies one offer observation to the basket it answers for — element 10's application path, the one an event or a read
     * feeds and the one DoD20/DoD21 prove (D39). The transport itself is deferred.
     *
     * <p>The write happens only when the document changed: a duplicate delivery and an observation older than the row's read
     * are answered from the state that is already there, so neither costs a write nor a version (EVT-3, EVT-4).
     *
     * @param basketId the basket the observation answers for
     * @param command  the observation, already mapped from the port's read record
     * @return the basket as the read side serves it
     */
    Basket applyObservation(BasketId basketId, OfferObservationCommand command) {
        BasketEditor basket = editor(basketId);
        return basket.applyObservation(command) ? store(basket) : basket.toSnapshot();
    }

    Basket removeItem(BasketId basketId, String offerId) {
        BasketEditor basket = editor(basketId);
        basket.removeItem(offerId);
        return store(basket);
    }

    Basket removeSelectedItems(BasketId basketId) {
        BasketEditor basket = editor(basketId);
        basket.removeSelectedItems();
        return store(basket);
    }

    Basket removeEverything(BasketId basketId) {
        BasketEditor basket = editor(basketId);
        basket.removeEverything();
        return store(basket);
    }

    Basket select(BasketId basketId, BasketSelection selection, boolean selected) {
        BasketEditor basket = editor(basketId);
        basket.select(selection, selected);
        return store(basket);
    }

    /**
     * Hands the selection over (HAND-1…HAND-6) and answers the projection element 09 §Shape of record fixes: the payload is
     * composed here — the rows the aggregate refuses nothing about any more, the totals of element 04, the range of element 05
     * — and checkout receives it through the kernel port, which is the only crossing between the two slices.
     *
     * <p>The refusals happen before the port: nothing selected, a blocked row and an inactive basket are the aggregate's own
     * answers, so a refused handover reaches no port and writes nothing. The totals and the range are computed beforehand only
     * because the identity covers them, and both are pure calculations over the same snapshot. A repeat of unchanged content is
     * not sent at all — the digest in {@code lastHandover} already answered it (HAND-4, DoD30). The basket stays active and
     * editable throughout (HAND-6), and no order exists anywhere in this service (HAND-3).
     *
     * <p>The order of the two writes is deliberate and one-sided: the port is asked first, so a lost optimistic lock leaves
     * checkout holding a handover the basket did not record. Nothing reads it back while checkout creates no order (HAND-3), and
     * a retry recomposes the same revision and answers the receipt that already exists — but D4's checkout is the one that has to
     * confirm the pairing once it creates one.
     *
     * @param basketId the basket the path carries, which is also the handover's identity (D32)
     * @param channel  how the customer arrives, which decides whether delivery is charged (element 04 S6)
     * @return the answer the client reads
     */
    HandoverAnswer handOver(BasketId basketId, Channel channel) {
        BasketEditor basket = editor(basketId);
        Handover handover = recordedFor(basket, channel);
        baskets.save(basket);
        return HandoverAnswer.of(handover);
    }

    private Handover recordedFor(BasketEditor basket, Channel channel) {
        Instant now = Instant.now(clock);
        Basket snapshot = basket.toSnapshot();
        Handover.Totals totals = totalsOf(calculation.calculate(snapshot, channel));
        HandoverDraft draft = basket.handOver(now, totals);
        Estimate estimate = estimates.latestTo(snapshot.items(), channel, now);
        return sentUnlessRepeated(draft, handoverOf(snapshot, draft, totals, estimate));
    }

    private Handover sentUnlessRepeated(HandoverDraft draft, Handover handover) {
        if (!draft.repeated()) {
            checkouts.accept(handover);
        }
        return handover;
    }

    private static Handover handoverOf(Basket snapshot, HandoverDraft draft, Handover.Totals totals, Estimate estimate) {
        LastHandover identity = draft.identity();
        return new Handover(identity.handoverId(), snapshot.basketId().value(), snapshot.customerId().value(),
                snapshot.currency(), identity.revision(), identity.createdAt(), identity.contentDigest(),
                draft.selection(), totals, estimate);
    }

    private void observed(BasketEditor basket) {
        offers.read(basket.toSnapshot().items().stream().map(BasketItem::offerId).toList()).stream()
                .map(OfferObservationCommand::of)
                .forEach(basket::applyObservation);
    }

    private BasketContent content(Basket snapshot, Channel channel) {
        return BasketContent.of(snapshot, calculation.calculate(snapshot, channel),
                estimates.latestTo(snapshot.items(), channel, Instant.now(clock)),
                calculation.delivery().rateFor(channel, snapshot.currency()));
    }

    private static boolean holds(BasketEditor basket, String offerId) {
        return basket.toSnapshot().items().stream().anyMatch(item -> item.offerId().equals(offerId));
    }

    /**
     * The row an offer the basket does not hold yet is added as — read, never inferred.
     *
     * <p>Two answers cannot become a row. An offer the provider no longer has is {@code OFFER_NOT_FOUND}: the customer asked
     * for something nobody sells (step 10 of the contract). A read that failed is {@code OFFER_READ_UNAVAILABLE}: there is no
     * last-known snapshot to grey a row with, so the one failing read a render tolerates (A4) is the one an add cannot (element
     * 07 §Error contract, 503). The same holds for a partial answer that names no title, picture or price: a row renders all of
     * them, so an answer that carries none of them describes no row (element 02 RULE-8).
     */
    private BasketItem offered(String offerId, int quantity) {
        return offers.read(List.of(offerId)).stream()
                .filter(record -> record.offerId().equals(offerId))
                .findFirst()
                .map(record -> rowOf(record, quantity))
                .orElseThrow(() -> new ShoppingException(ShoppingException.Error.OFFER_READ_UNAVAILABLE, offerId));
    }

    private static BasketItem rowOf(OfferRecord record, int quantity) {
        if (OfferObservationCommand.of(record) instanceof OfferObservationCommand.Answered answered) {
            return new BasketItem(answered.offerId(), answered.seller(), quantity, true,
                    OfferAvailabilityDecision.decide(answered.observation()),
                    describedBy(answered.snapshot(), record.offerId()), record.readAt());
        }
        throw record.offerState() == OfferObservation.OfferState.notRead
                ? new ShoppingException(ShoppingException.Error.OFFER_READ_UNAVAILABLE, record.offerId())
                : new ShoppingException(ShoppingException.Error.OFFER_NOT_FOUND, record.offerId());
    }

    private static BasketItem.LastKnown describedBy(OfferObservationCommand.OfferSnapshot snapshot, String offerId) {
        boolean describesNothing = snapshot.title() == null || snapshot.pictureUrl() == null
                || snapshot.currentPrice() == null || snapshot.discount() == null;
        if (describesNothing) {
            throw new ShoppingException(ShoppingException.Error.OFFER_READ_UNAVAILABLE, offerId);
        }
        return new BasketItem.LastKnown(snapshot.title(), snapshot.pictureUrl(), snapshot.currentPrice(),
                snapshot.discount());
    }

    private static Handover.Totals totalsOf(BasketPrice price) {
        return new Handover.Totals(price.productValue(), price.discountValue(), price.deliveryValue(),
                price.totalWithDelivery());
    }

    private BasketEditor basketFor(CustomerId customerId) {
        return baskets.findActiveFor(customerId).orElseGet(() -> open(customerId));
    }

    private BasketEditor open(CustomerId customerId) {
        BasketEditor opened = BasketEditor.openBasketFor(BasketId.minted(), customerId, currency.code());
        try {
            baskets.save(opened);
            return opened;
        } catch (ShoppingException taken) {
            return baskets.findActiveFor(customerId).orElseThrow(() -> taken);
        }
    }

    private BasketEditor editor(BasketId basketId) {
        Objects.requireNonNull(basketId, "basketId");
        return baskets.get(basketId.value()).orElseThrow(
                () -> new ShoppingException(ShoppingException.Error.BASKET_NOT_FOUND, basketId.value()));
    }

    private Basket store(BasketEditor basket) {
        baskets.save(basket);
        return basket.toSnapshot();
    }
}
