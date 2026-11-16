package shop.catalog.pricing;

import java.util.Objects;

/**
 * What a customer pays for one offer in one currency at one instant (10). {@code ABSENT} is a named answer — the
 * offer carries no open price entry — and never the amount {@code 0}, which is a real price (RULE-7 of 13).
 */
record ActivePrice(ActivePrice.State state, Money amount) {

    ActivePrice {
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

    enum State {
        PRICED, ABSENT
    }
}
