package shop.shopping.basket;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.util.List;

/**
 * The facts the basket aggregate has already recorded — one union, one nested record per fact, versioned names so a stored
 * history could be read back (element 02 §Domain events).
 *
 * <p>There is no event for a selection change and none for opening a basket: selection is state, not history (RULE-10), and
 * the specification names no creation fact.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME)
@JsonSubTypes({
        @JsonSubTypes.Type(value = DomainEvent.AddedToBasket.class, name = "AddedToBasket_v1"),
        @JsonSubTypes.Type(value = DomainEvent.BasketQuantityChanged.class, name = "BasketQuantityChanged_v1"),
        @JsonSubTypes.Type(value = DomainEvent.RemovedFromBasket.class, name = "RemovedFromBasket_v1"),
        @JsonSubTypes.Type(value = DomainEvent.GoToCheckout.class, name = "GoToCheckout_v1")
})
sealed interface DomainEvent {

    /**
     * An offer entered the basket, or a repeated add merged into the row that was already there (D19).
     *
     * <p>{@code quantity} is the row's quantity after the merge, never the delta the command carried: a listener must not
     * have to know what the row held before to render the basket, and element 02's {@code addedToBasket} sample reads the
     * same way for a first add.
     */
    record AddedToBasket(BasketId basketId, String offerId, int quantity) implements DomainEvent {
    }

    /** One row's quantity was replaced; the board lacks this fact and the stepper requires it (R4, D19). */
    record BasketQuantityChanged(BasketId basketId, String offerId, int from, int to) implements DomainEvent {
    }

    /** A row left the basket, whatever scope removed it (element 02 §Behaviour). */
    record RemovedFromBasket(BasketId basketId, String offerId) implements DomainEvent {
    }

    /** The selection crossed to checkout; the basket is neither cleared nor locked (HAND-6). */
    record GoToCheckout(BasketId basketId, List<String> selection) implements DomainEvent {

        public GoToCheckout {
            selection = List.copyOf(selection);
        }
    }
}
