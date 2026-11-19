package shop.fulfillment.processing;

import java.util.Objects;

/**
 * The decision that an employee's claim on one order is accepted — the only concurrency control the sources name:
 * one employee per order, exclusive and not advisory (element 07).
 *
 * <p>It owns no state: the assignee lives in {@link OrderProcessingEditor}, and this record only computes GRANT,
 * NO-OP or REFUSE. It is pure — no I/O, no clock, no timeout, no retry — and a refusal never clears the existing
 * assignee. The table is {@link Rule}, declared in evaluation order, first match wins.
 */
record AssignmentGuard(ProcessingState orderState, String assignee, String requester) {

    /** The verdict as the caller applies it. */
    sealed interface Verdict {

        /** The claim is accepted: the requester becomes the assignee. */
        record Grant() implements Verdict {
        }

        /** The claim repeats the one already held: no event, no write. */
        record NoOp() implements Verdict {
        }

        /** The claim is refused and the current state stays untouched. */
        record Refuse(Reason reason) implements Verdict {
        }
    }

    /** Why a claim is refused, in the decision table's own words. */
    enum Reason {
        ACTOR,
        ALREADY_ASSIGNED,
        PAST_PICKING,
        CLOSED
    }

    /** DT-07-1 … DT-07-6, declared in the order they are evaluated. */
    enum Rule {
        DT_07_1(new Verdict.Grant()),
        DT_07_2(new Verdict.Refuse(Reason.ACTOR)),
        DT_07_3(new Verdict.Refuse(Reason.ALREADY_ASSIGNED)),
        DT_07_4(new Verdict.NoOp()),
        DT_07_5(new Verdict.Refuse(Reason.PAST_PICKING)),
        DT_07_6(new Verdict.Refuse(Reason.CLOSED));

        private final Verdict verdict;

        Rule(Verdict verdict) {
            this.verdict = verdict;
        }

        Verdict verdict() {
            return verdict;
        }

        boolean appliesTo(ProcessingState state, String assignee, String requester) {
            return switch (this) {
                case DT_07_1 -> state == ProcessingState.PENDING && requester != null;
                case DT_07_2 -> state == ProcessingState.PENDING;
                case DT_07_3 -> holdsAPosition(state) && assignee != null
                        && !Objects.equals(assignee, requester);
                case DT_07_4 -> holdsAPosition(state) && assignee != null
                        && Objects.equals(assignee, requester);
                case DT_07_5 -> state == ProcessingState.READY_FOR_DELIVERY;
                case DT_07_6 -> state == ProcessingState.DELIVERED;
            };
        }

        private static boolean holdsAPosition(ProcessingState state) {
            return switch (state) {
                case ASSIGNED, PICKING, PACKED, ON_HOLD -> true;
                default -> false;
            };
        }
    }

    /**
     * Decides one claim.
     *
     * @param orderState the processing state the claim lands on
     * @param assignee   the employee holding the order, or {@code null} when nobody does
     * @param requester  the claiming employee, or {@code null} when the caller is not one
     * @return the verdict to apply
     */
    static Verdict decide(ProcessingState orderState, String assignee, String requester) {
        return new AssignmentGuard(orderState, assignee, requester).decide();
    }

    Verdict decide() {
        Verdict verdict = Rule.DT_07_6.verdict();
        for (Rule rule : Rule.values()) {
            if (rule.appliesTo(orderState, assignee, requester)) {
                verdict = rule.verdict();
                break;
            }
        }
        return verdict;
    }
}
