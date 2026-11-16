package shop.catalog.pricing;

import java.util.Objects;

/**
 * What a customer pays for one offer in one currency at one instant (10). {@code ABSENT} is a named answer — the
 * offer carries no open price entry — and never the amount {@code 0}, which is a real price (RULE-7 of 13).
 *
 * <p>Public because it is the answer a sibling context asks for: the pricing view of 16 shows the effect of stacking before
 * anything is saved, so {@code reads} asks through a contract port and a mediator and receives this value unchanged
 * ({@code mapping.port-snapshot}). {@link Money} leaves with it, since the amount is minor units plus its currency (D1).
 */
public record ActivePrice(State state, Money amount) {

    public ActivePrice {
        Objects.requireNonNull(state, "state");
        boolean complete = state == State.PRICED ? amount != null : amount == null;
        if (!complete) {
            throw new PricingException(PricingException.Error.ACTIVE_PRICE_INCOMPLETE, state);
        }
    }

    static ActivePrice priced(Money amount) {
        return new ActivePrice(State.PRICED, amount);
    }

    static ActivePrice absent() {
        return new ActivePrice(State.ABSENT, null);
    }

    boolean isAbsent() {
        return state == State.ABSENT;
    }

    /** The two answers of 10; only {@link #PRICED} carries an amount. */
    public enum State {
        PRICED,
        ABSENT
    }
}
