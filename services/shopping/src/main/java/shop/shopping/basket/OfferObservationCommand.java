package shop.shopping.basket;

import java.time.Instant;
import java.util.Objects;

import shop.shopping.kernel.Money;

/**
 * Element 10's application path as one value: a single observation of a single offer, ready to be applied to its row (D39 —
 * the transport that would carry it is deferred, the application is not).
 *
 * <p>It has three shapes because element 10 §Events consumed answers three different questions, and a row's availability may
 * only be re-decided from an answer that actually said something about it:
 *
 * <ul>
 *   <li>{@link Answered} — a read that described the offer: availability re-decided, snapshot replaced, seller regrouped;</li>
 *   <li>{@link Unanswered} — a read that described nothing (a {@code 401}, a timeout, a {@code 5xx}, an id a {@code 200}
 *       omitted): availability re-decided, snapshot and seller kept (A3, A4);</li>
 *   <li>{@link Refreshed} — a partial observation, which is what {@code product prices changed} and
 *       {@code product description snapshot when published} are: they carry no {@code offerState} and no {@code stock}, so
 *       they refresh content and leave the availability the row already has, because element 06 is not consulted about
 *       something nobody answered.</li>
 * </ul>
 *
 * <p>Without the third shape an adapter for those two events would have to invent an {@code offerState} and a {@code stock}
 * to build a command, and the invented values would re-decide a row into {@code outOfStock} or {@code unavailable} on a price
 * change. What a command never carries is the list {@code price}: it is in {@link OfferRecord} and in nothing else, so no
 * shape here can price a line (D53). A row is refreshed by replacing its snapshot, never by adding to it, which is what makes
 * a duplicate delivery a no-op instead of a double count (EVT-4).
 */
sealed interface OfferObservationCommand
        permits OfferObservationCommand.Answered, OfferObservationCommand.Unanswered, OfferObservationCommand.Refreshed {

    /**
     * The command one read record maps to — the single place a record becomes something the aggregate may apply.
     *
     * @param record the offer as the catalog answered it, never absent
     * @return the command applying exactly that answer
     */
    static OfferObservationCommand of(OfferRecord record) {
        Objects.requireNonNull(record, "record");
        OfferRecord.OfferDescription description = record.description();
        return description == null
                ? new Unanswered(record.observation())
                : new Answered(record.observation(), OfferSnapshot.of(description), record.sellerOrPlaceholder());
    }

    /**
     * A partial observation of one offer — element 10's {@code product prices changed} and {@code product description
     * snapshot when published}, which carry no availability and therefore decide none.
     *
     * @param offerId  the row to refresh
     * @param readAt   when the change was reported
     * @param snapshot what changed, with every absent field answered by the row's own snapshot
     * @return the command the change is applied with
     */
    static OfferObservationCommand refreshed(String offerId, Instant readAt, OfferSnapshot snapshot) {
        return new Refreshed(offerId, readAt, snapshot);
    }

    /**
     * The offer this observation answers for, which is the row it is matched on — an unknown one creates nothing (EVT-2).
     *
     * @return the identity, never absent
     */
    String offerId();

    /**
     * When the answer was obtained. Two observations of one offer are applied in this order, and an older one is discarded
     * (EVT-3, DoD21).
     *
     * @return the instant, never absent
     */
    Instant readAt();

    /**
     * The offer as the read answered it: the row's snapshot is replaced with what the provider said, its seller regrouped,
     * and its availability re-decided from the same observation (element 06, EVT-4, element 02 RULE-8).
     *
     * @param observation the offer as the last read saw it
     * @param snapshot    what the provider said about the content, already free of the list price (D53)
     * @param seller      the shipment the row is grouped into, never absent (02 RULE-7)
     */
    record Answered(OfferObservation observation, OfferSnapshot snapshot, String seller)
            implements OfferObservationCommand {

        public Answered {
            Objects.requireNonNull(observation, "observation");
            Objects.requireNonNull(snapshot, "snapshot");
            Objects.requireNonNull(seller, "seller");
        }

        @Override
        public String offerId() {
            return observation.offerId();
        }

        @Override
        public Instant readAt() {
            return observation.readAt();
        }
    }

    /**
     * An observation that describes nothing: the read failed (A4) or the provider's {@code 200} did not hold the offer (A3).
     * Availability is decided again — fail-closed, never keeping an earlier {@code available} — and the row keeps its
     * last-known snapshot and its seller.
     *
     * @param observation the non-answer, carrying the identity and the instant it was obtained
     */
    record Unanswered(OfferObservation observation) implements OfferObservationCommand {

        public Unanswered {
            Objects.requireNonNull(observation, "observation");
        }

        @Override
        public String offerId() {
            return observation.offerId();
        }

        @Override
        public Instant readAt() {
            return observation.readAt();
        }
    }

    /**
     * A partial observation: the offer's content changed and its availability was not answered — {@code product prices
     * changed} and {@code product description snapshot when published} of element 10 §Events consumed.
     *
     * <p>Nothing is decided here. The row keeps the availability element 06 last gave it, because a price a provider changed
     * says nothing about whether the offer can be sold (R13: an event is never a reason to grey, only to refresh), and only
     * the next read is authoritative about that (EVT-3).
     *
     * @param offerId  the row to refresh
     * @param readAt   when the change was reported, which orders it against the row's read
     * @param snapshot what changed, with every absent field answered by the row's own snapshot
     */
    record Refreshed(String offerId, Instant readAt, OfferSnapshot snapshot) implements OfferObservationCommand {

        public Refreshed {
            Objects.requireNonNull(offerId, "offerId");
            Objects.requireNonNull(readAt, "readAt");
            Objects.requireNonNull(snapshot, "snapshot");
        }
    }

    /**
     * What an answer says about the offer's content, field by field: element 08's table keeps the row's last-known value for
     * every field the provider left out, so a {@code 200} without a title keeps the title the row already renders instead of
     * blanking it (element 02 RULE-8).
     *
     * <p>It exists so that a partial answer never has to be completed by fabrication, and it carries no list price: the two
     * prices here are the ones a row is priced with, each with its kind stated as it enters the snapshot — a {@code charge}
     * and a {@code discount} (element 03 RULE-6, D40).
     *
     * @param title        the product title, absent when the provider named none
     * @param pictureUrl   the one picture the row shows, absent when the provider named none
     * @param currentPrice the price the offer is sold at, absent when the provider named none
     * @param discount     the flattened discount, absent when the provider named none
     */
    record OfferSnapshot(String title, String pictureUrl, Money currentPrice, Money discount) {

        public OfferSnapshot {
            ensureOneCurrency(currentPrice, discount);
        }

        /**
         * The answer as the port's record states it — the one place a record's content becomes something a row may hold
         * (D53: the record's list price is dropped here).
         *
         * @param description the provider's answer, never absent
         * @return the snapshot a row is refreshed with
         */
        static OfferSnapshot of(OfferRecord.OfferDescription description) {
            Objects.requireNonNull(description, "description");
            return new OfferSnapshot(description.title(), description.pictureUrl(), description.currentPrice(),
                    description.discount());
        }

        /**
         * The row's snapshot after this answer: every field the answer named is taken with its kind stated, every field it
         * left out is kept from {@code lastKnown}.
         *
         * @param lastKnown the snapshot the row already holds, never absent
         * @return what the row holds after this answer
         */
        BasketItem.LastKnown filledWith(BasketItem.LastKnown lastKnown) {
            Objects.requireNonNull(lastKnown, "lastKnown");
            return new BasketItem.LastKnown(
                    title == null ? lastKnown.title() : title,
                    pictureUrl == null ? lastKnown.picture() : pictureUrl,
                    currentPrice == null ? lastKnown.currentPrice() : asCharge(currentPrice),
                    discount == null ? lastKnown.discount() : asDiscount(discount));
        }

        private static Money asCharge(Money amount) {
            return new Money(amount.amount(), amount.currency(), Money.Kind.charge);
        }

        private static Money asDiscount(Money amount) {
            return new Money(amount.amount(), amount.currency(), Money.Kind.discount);
        }

        private static void ensureOneCurrency(Money currentPrice, Money discount) {
            if (currentPrice != null && discount != null
                    && !currentPrice.currency().equals(discount.currency())) {
                throw new IllegalArgumentException("one offer is priced in one currency, got " + currentPrice
                        + " and " + discount);
            }
        }
    }
}
