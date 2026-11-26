package shop.shopping.basket;

import lombok.AllArgsConstructor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

import shop.shopping.kernel.Availability;
import shop.shopping.kernel.Handover;
import shop.shopping.kernel.LastHandover;
import shop.shopping.kernel.Money;
import shop.shopping.kernel.ShoppingException;

/**
 * The customer's basket as the only object allowed to change it (element 02): an editor aggregate, always editable, with no
 * freeze step and no reservation.
 *
 * <p>Every command answers one business operation and every refusal is {@link ShoppingException}: a repeated add merges the
 * quantity and selects the row (RULE-4, D19), a quantity is at least one (RULE-3), an {@code unavailable} row keeps its
 * last-known snapshot and only its quantity is frozen (RULE-5, RULE-8, D19), a selection never exceeds the basket (RULE-9)
 * and produces no fact (RULE-10), and a handover neither clears nor locks anything (HAND-6). Availability is data the item
 * carries — this aggregate never decides it (element 06).
 *
 * <p>Nothing here reserves stock (RULE-2), reads a clock, publishes, or asks anybody anything: the caller passes the instant
 * a handover is composed at, and the repository drains the event buffer and publishes it.
 */
@AllArgsConstructor
class BasketEditor {

    final BasketId basketId;

    final List<DomainEvent> events;

    private final CustomerId customerId;
    private final String currency;
    private List<BasketItem> items;
    private BasketState state;
    private long revision;
    private LastHandover lastHandover;

    /**
     * Opens the basket a customer always has: active, empty, in the one currency it will hold, at revision zero and with no
     * handover recorded yet (RULE-1, D42).
     *
     * @param basketId   the identity minted for this basket
     * @param customerId the customer whose basket it is
     * @param currency   the one currency the basket holds
     * @return an empty active basket
     */
    static BasketEditor openBasketFor(BasketId basketId, CustomerId customerId, String currency) {
        Objects.requireNonNull(basketId, "basketId");
        Objects.requireNonNull(customerId, "customerId");
        return new BasketEditor(basketId, new ArrayList<>(), customerId, Money.zero(currency).currency(),
                new ArrayList<>(), BasketState.active, 0, null);
    }

    /**
     * Adds an offer, or merges into the row that already stands for it (D19).
     *
     * <p>A row that is already there keeps its seller, its availability and its last-known snapshot: a repeated add
     * contributes a quantity and selects the row, while refreshing an observation is element 06's command, not this one.
     *
     * @param item the offer as it was read — quantity, seller, availability and last-known snapshot
     */
    void addToBasket(BasketItem item) {
        Objects.requireNonNull(item, "item");
        ensureBasketIsActive();
        ensurePricesAreInTheBasketCurrency(item);
        BasketItem added = storeOrMerge(item);
        revision++;
        events.add(new DomainEvent.AddedToBasket(basketId, added.offerId(), added.quantity()));
    }

    /**
     * Replaces one row's quantity — refused on an {@code unavailable} row and allowed on an {@code outOfStock} one (D19).
     *
     * @param offerId  the row to change
     * @param quantity the absolute quantity the client wants, never a relative change (element 07, F16)
     */
    void changeQuantity(String offerId, int quantity) {
        Objects.requireNonNull(offerId, "offerId");
        ensureBasketIsActive();
        ensureQuantityIsAtLeastOne(quantity);
        BasketItem stored = storedItemOrRefuse(offerId);
        ensureQuantityCanChange(stored);
        replaceQuantity(stored, quantity);
    }

    /**
     * Removes one row. An emptied basket stays active with no items (element 02 §Behaviour).
     *
     * @param offerId the row to remove
     */
    void removeItem(String offerId) {
        Objects.requireNonNull(offerId, "offerId");
        ensureBasketIsActive();
        storedItemOrRefuse(offerId);
        removeMatching(item -> item.offerId().equals(offerId));
    }

    /** Removes every selected row — the bulk action of the screen (D18). */
    void removeSelectedItems() {
        ensureBasketIsActive();
        removeMatching(BasketItem::selected);
    }

    /** Removes every row, leaving an empty active basket (D18, Q27). */
    void removeEverything() {
        ensureBasketIsActive();
        removeMatching(item -> true);
    }

    /**
     * Selects or deselects the rows one scope names — the only command that changes state without a fact, because selection
     * is state and not history (RULE-10).
     *
     * <p>A scope that names one offer the basket does not hold is refused exactly as a quantity change is: element 07's
     * {@code 404} row covers an unknown offer on either command. A shipment or the whole basket that currently holds nothing
     * is a no-op instead, because the basket is still a valid, empty one (RULE-9).
     *
     * @param selection which rows the command covers
     * @param selected  switch them on or off
     */
    void select(BasketSelection selection, boolean selected) {
        Objects.requireNonNull(selection, "selection");
        ensureBasketIsActive();
        ensureTheNamedOfferIsInTheBasket(selection);
        long uncovered = items.stream().filter(item -> selection.covers(item) && item.selected() != selected).count();
        if (uncovered > 0) {
            items.replaceAll(item -> selection.covers(item) ? item.withSelection(selected) : item);
            revision++;
        }
    }

    /**
     * Composes what crosses to checkout: the selected rows, the revision they are handed over at, and the identity that makes
     * a repeat of the same content the same handover (HAND-1…HAND-4, D41).
     *
     * <p>The basket is neither cleared nor locked (HAND-6): only {@code revision} and {@code lastHandover} move, and only when
     * the content differs from the handover already recorded.
     *
     * @param now the instant the handover is composed at
     * @return the payload, carrying the identity of this content
     */
    Handover handOver(Instant now) {
        Objects.requireNonNull(now, "now");
        ensureBasketIsActive();
        ensureSelectionCanCross();
        List<Handover.Line> selection = handoverLines();
        LastHandover recorded = recordedFor(selection, now);
        return new Handover(recorded.handoverId(), basketId.value(), customerId.value(), currency,
                recorded.revision(), recorded.createdAt(), selection);
    }

    /**
     * Reads the basket as an immutable snapshot.
     *
     * @return the state as the read side stores and serves it
     */
    Basket toSnapshot() {
        return new Basket(basketId, customerId, currency, state, List.copyOf(items), revision, lastHandover);
    }

    private BasketItem storeOrMerge(BasketItem item) {
        Optional<BasketItem> stored = storedItem(item.offerId());
        BasketItem added = stored.map(existing -> existing.merging(item.quantity()))
                .orElseGet(() -> item.withSelection(true));
        stored.ifPresentOrElse(existing -> items.set(items.indexOf(existing), added), () -> items.add(added));
        return added;
    }

    private void replaceQuantity(BasketItem stored, int quantity) {
        if (stored.quantity() != quantity) {
            items.set(items.indexOf(stored), stored.withQuantity(quantity));
            revision++;
            events.add(new DomainEvent.BasketQuantityChanged(
                    basketId, stored.offerId(), stored.quantity(), quantity));
        }
    }

    private void removeMatching(Predicate<BasketItem> scope) {
        List<BasketItem> removed = items.stream().filter(scope).toList();
        if (!removed.isEmpty()) {
            items.removeAll(removed);
            removed.forEach(item -> events.add(new DomainEvent.RemovedFromBasket(basketId, item.offerId())));
            revision++;
        }
    }

    private LastHandover recordedFor(List<Handover.Line> selection, Instant now) {
        String digest = Handover.contentDigestOf(basketId.value(), customerId.value(), currency, selection);
        LastHandover recorded = isAlreadyRecorded(digest) ? lastHandover : recordHandover(digest, now);
        return recorded;
    }

    private List<Handover.Line> handoverLines() {
        return selectedItems().stream().map(BasketEditor::lineOf).toList();
    }

    private static Handover.Line lineOf(BasketItem item) {
        return new Handover.Line(item.offerId(), item.seller(), item.quantity(), item.availability(),
                item.lastKnown().currentPrice(), item.lastKnown().discount());
    }

    private LastHandover recordHandover(String digest, Instant now) {
        revision++;
        lastHandover = new LastHandover(revision, digest, basketId.value(), now);
        events.add(new DomainEvent.GoToCheckout(basketId, selectedOfferIds()));
        return lastHandover;
    }

    private boolean isAlreadyRecorded(String digest) {
        return lastHandover != null && lastHandover.contentDigest().equals(digest);
    }

    private List<BasketItem> selectedItems() {
        return items.stream().filter(BasketItem::selected).toList();
    }

    private List<String> selectedOfferIds() {
        return selectedItems().stream().map(BasketItem::offerId).toList();
    }

    private Optional<BasketItem> storedItem(String offerId) {
        return items.stream().filter(item -> item.offerId().equals(offerId)).findFirst();
    }

    private BasketItem storedItemOrRefuse(String offerId) {
        return storedItem(offerId).orElseThrow(
                () -> new ShoppingException(ShoppingException.Error.OFFER_NOT_FOUND, offerId));
    }

    private void ensureBasketIsActive() {
        if (state != BasketState.active) {
            throw new ShoppingException(ShoppingException.Error.BASKET_INACTIVE, basketId.value(), state);
        }
    }

    private void ensurePricesAreInTheBasketCurrency(BasketItem item) {
        if (!currency.equals(item.currency())) {
            throw new ShoppingException(ShoppingException.Error.CURRENCY_MISMATCH, item.currency(), currency);
        }
    }

    private void ensureQuantityIsAtLeastOne(int quantity) {
        if (quantity < 1) {
            throw new ShoppingException(ShoppingException.Error.QUANTITY_NOT_POSITIVE, quantity);
        }
    }

    private void ensureQuantityCanChange(BasketItem item) {
        if (item.availability().status() == Availability.Status.unavailable) {
            throw new ShoppingException(ShoppingException.Error.OFFER_UNAVAILABLE, item.offerId());
        }
    }

    private void ensureTheNamedOfferIsInTheBasket(BasketSelection selection) {
        if (selection instanceof BasketSelection.Item named && items.stream().noneMatch(selection::covers)) {
            throw new ShoppingException(ShoppingException.Error.OFFER_NOT_FOUND, named.offerId());
        }
    }

    private void ensureSelectionCanCross() {
        List<BasketItem> selected = selectedItems();
        if (selected.isEmpty()) {
            throw new ShoppingException(ShoppingException.Error.NOTHING_SELECTED, basketId.value());
        }
        List<BasketItem> blocked = selected.stream().filter(item -> item.availability().blocksHandover()).toList();
        if (!blocked.isEmpty()) {
            throw new ShoppingException(ShoppingException.Error.BLOCKED_BY_ITEMS, basketId.value(), blocked);
        }
    }
}
