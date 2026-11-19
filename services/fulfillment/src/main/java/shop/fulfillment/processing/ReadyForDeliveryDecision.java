package shop.fulfillment.processing;

import shop.fulfillment.kernel.CarrierPort;
import shop.fulfillment.kernel.ProcessingState;

/**
 * The decision that a packed order leaves the warehouse's hands — the only place where "the order is done" is decided
 * (element 08).
 *
 * <p>A grant has two effects in one command: the local mark on {@link OrderFulfillmentProcess} and the carrier's
 * collection request through {@link CarrierPort}. It is pure — it reads its three inputs and answers; the caller
 * executes, local effect first, as the source sentence is ordered. The hold is a state, never a flag beside one, so a
 * held order is simply {@code onHold} and is refused as such.
 *
 * <p>The table is {@link Rule}; its declaration order is the evaluation order the element fixes — a missing parcel is
 * refused before anything else, so no rule can produce an effect without one.
 */
record ReadyForDeliveryDecision(ProcessingState orderState, boolean positionsAllPacked, boolean parcelRegistered) {

    /** The verdict as the caller applies it. */
    sealed interface Verdict {

        /** Mark the order ready and ask the carrier to collect. */
        record Grant() implements Verdict {
        }

        /** A replay of a grant already applied: no second mark, no second collection. */
        record NoOp() implements Verdict {
        }

        /** Refused; neither effect runs and nothing is written. */
        record Refuse(Reason reason) implements Verdict {
        }
    }

    /** Why readiness is refused, in the decision table's own words. */
    enum Reason {
        POSITIONS_OPEN("positionsOpen"),
        NOT_PACKED("notPacked"),
        ON_HOLD("onHold"),
        PAST_HANDOVER("pastHandover"),
        NO_PARCEL("noParcel");

        private final String wireName;

        Reason(String wireName) {
            this.wireName = wireName;
        }

        /**
         * The reason as the contract of element 11 spells it in {@code 409 NOT_READY.reasons[]} — the table's own value,
         * never a code and never an object (element 08, element 11).
         *
         * @return the reason value the refusal names
         */
        String wireName() {
            return wireName;
        }
    }

    /** DT-08-7, DT-08-4, DT-08-3, DT-08-2, DT-08-1, DT-08-5, DT-08-6 — the element's evaluation order. */
    enum Rule {
        DT_08_7(new Verdict.Refuse(Reason.NO_PARCEL)),
        DT_08_4(new Verdict.Refuse(Reason.ON_HOLD)),
        DT_08_3(new Verdict.Refuse(Reason.NOT_PACKED)),
        DT_08_2(new Verdict.Refuse(Reason.POSITIONS_OPEN)),
        DT_08_1(new Verdict.Grant()),
        DT_08_5(new Verdict.NoOp()),
        DT_08_6(new Verdict.Refuse(Reason.PAST_HANDOVER));

        private final Verdict verdict;

        Rule(Verdict verdict) {
            this.verdict = verdict;
        }

        Verdict verdict() {
            return verdict;
        }

        boolean appliesTo(ProcessingState state, boolean positionsAllPacked, boolean parcelRegistered) {
            return switch (this) {
                case DT_08_7 -> !parcelRegistered;
                case DT_08_4 -> state == ProcessingState.ON_HOLD;
                case DT_08_3 -> state == ProcessingState.PENDING
                        || state == ProcessingState.ASSIGNED
                        || state == ProcessingState.PICKING;
                case DT_08_2 -> state == ProcessingState.PACKED && !positionsAllPacked;
                case DT_08_1 -> state == ProcessingState.PACKED;
                case DT_08_5 -> state == ProcessingState.READY_FOR_DELIVERY;
                case DT_08_6 -> state == ProcessingState.DELIVERED;
            };
        }
    }

    /**
     * Decides one readiness command.
     *
     * @param orderState        the processing state the command lands on
     * @param positionsAllPacked whether every position is in the box
     * @param parcelRegistered  whether a carrier identity exists to address the collection request
     * @return the verdict to apply
     */
    static Verdict decide(ProcessingState orderState, boolean positionsAllPacked, boolean parcelRegistered) {
        return new ReadyForDeliveryDecision(orderState, positionsAllPacked, parcelRegistered).decide();
    }

    Verdict decide() {
        Verdict verdict = Rule.DT_08_6.verdict();
        for (Rule rule : Rule.values()) {
            if (rule.appliesTo(orderState, positionsAllPacked, parcelRegistered)) {
                verdict = rule.verdict();
                break;
            }
        }
        return verdict;
    }
}
