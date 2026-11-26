package shop.shopping.basket;

import java.time.Instant;
import java.util.Objects;

/**
 * One offer as it was last read, and the only input of element 06's decision: what the offer is ({@link OfferState}), how
 * many units the warehouse has ({@link Stock}), and when the answer was obtained.
 *
 * <p>It is the slice of element 08's {@code OfferCatalog} read that the decision consumes — one per offered id of a batched
 * read — and it stays in the basket slice with the decision and the port, because {@code kernel/} holds only what crosses
 * into the checkout slice (element 09 rec 1) and nothing here crosses. The port's own read record carries more
 * ({@code seller}, {@code title}, prices, D22); none of it decides availability, so it is not here.
 *
 * <p>Neither a failed read nor an unanswered warehouse is an absence: a timeout, a {@code 5xx} and a {@code 401}/{@code 403}
 * arrive as {@link OfferState#notRead}, so a read that failed is an observation the decision can answer (A4, D26), and
 * {@link Stock.NotRead} says the offer answered while only its stock did not (A5) instead of a number that would mean
 * "unknown".
 *
 * <p>{@link #readAt()} is the instant the read answered. The decision never consults it — freshness is event-driven and no
 * timer exists (Q23, D25) — it is carried so element 08 discards an observation older than the one already applied to the
 * item (D39).
 *
 * @param offerId    the offer this observation answers for, the identity the application path matches an item on
 * @param offerState what the catalog said the offer is, or that nobody managed to read it
 * @param stock      what the warehouse said it has, or that it did not answer
 * @param readAt     when the read answered, which orders two observations of the same offer
 */
record OfferObservation(String offerId, OfferState offerState, Stock stock, Instant readAt) {

    /** The catalog's whole answer about the offer itself — the third state is a read that failed (A4). */
    enum OfferState {
        onSale,
        removed,
        notRead
    }

    /** The warehouse's answer: a count of units, or the explicit fact that it was never read (A5). */
    sealed interface Stock permits Stock.Quantity, Stock.NotRead {

        /**
         * A count of units — zero is a count, and it means none is left (A2). A negative one is not a count at all and reads
         * as none left; refusing it belongs to element 08's adapter, because no error code exists for a stock count.
         */
        record Quantity(int units) implements Stock {

            /** Whether any unit is left, which is the whole of A1 against A2. */
            boolean someLeft() {
                return units > 0;
            }
        }

        /** The warehouse did not answer at all: the offer is readable, only its stock is not (A5). */
        enum NotRead implements Stock {
            notRead
        }
    }

    OfferObservation {
        Objects.requireNonNull(offerId, "offerId");
        Objects.requireNonNull(offerState, "offerState");
        Objects.requireNonNull(stock, "stock");
        Objects.requireNonNull(readAt, "readAt");
    }
}
