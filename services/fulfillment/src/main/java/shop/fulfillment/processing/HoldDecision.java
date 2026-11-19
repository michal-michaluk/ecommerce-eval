package shop.fulfillment.processing;

/**
 * The decision that one issue stops one whole order, of what a hold blocks, and of what may lift it (element 09).
 *
 * <p>A hold is the state {@code onHold} on {@link OrderProcessingEditor}, not a flag beside it, and it is clock-free:
 * no carrier event, warehouse event or elapsed time lifts it. Only a resume that leaves zero open issues does, and
 * that same command is the one place an issue's {@code closedAt} is ever written.
 *
 * <p>The table is {@link Rule}, declared in the order the element lists it — the rows are disjoint on
 * {@code (command, state)}, so no precedence is needed and the last row is the default.
 */
record HoldDecision(Command command, ProcessingState orderState, int openIssues) {

    /** The two commands the table decides. */
    enum Command {
        REPORT_ISSUE,
        RESUME_HOLD
    }

    /** The verdict as the caller applies it. */
    sealed interface Verdict {

        /** Place the hold, or lift it and close the named issues. */
        record Grant() implements Verdict {
        }

        /** The hold does not move: a further issue on a held order, or a resume of an order that is not held. */
        record NoOp() implements Verdict {
        }

        /** Refused; no issue is recorded, no hold is placed or lifted. */
        record Refuse(Reason reason) implements Verdict {
        }
    }

    /** Why a hold command is refused, in the decision table's own words. */
    enum Reason {
        NOT_STARTED,
        PAST_HANDOVER,
        ISSUES_REMAIN
    }

    /** H1 … H7, declared in the order the element lists them. */
    enum Rule {
        H1(new Verdict.Grant()),
        H2(new Verdict.NoOp()),
        H3(new Verdict.Refuse(Reason.NOT_STARTED)),
        H4(new Verdict.Refuse(Reason.PAST_HANDOVER)),
        H5(new Verdict.Grant()),
        H6(new Verdict.Refuse(Reason.ISSUES_REMAIN)),
        H7(new Verdict.NoOp());

        private final Verdict verdict;

        Rule(Verdict verdict) {
            this.verdict = verdict;
        }

        Verdict verdict() {
            return verdict;
        }

        boolean appliesTo(Command command, ProcessingState state, int openIssues) {
            return switch (this) {
                case H1 -> firstIssue(command, state);
                case H2 -> furtherIssue(command, state, openIssues);
                case H3 -> issueBeforePicking(command, state);
                case H4 -> afterHandover(command, state);
                case H5 -> resumeWithNothingOpen(command, state, openIssues);
                case H6 -> resumeWithIssuesOpen(command, state);
                case H7 -> true;
            };
        }

        private static boolean firstIssue(Command command, ProcessingState state) {
            return command == Command.REPORT_ISSUE
                    && (state == ProcessingState.PICKING || state == ProcessingState.PACKED);
        }

        private static boolean furtherIssue(Command command, ProcessingState state, int openIssues) {
            return command == Command.REPORT_ISSUE
                    && state == ProcessingState.ON_HOLD && openIssues >= 1;
        }

        private static boolean issueBeforePicking(Command command, ProcessingState state) {
            return command == Command.REPORT_ISSUE
                    && (state == ProcessingState.PENDING || state == ProcessingState.ASSIGNED);
        }

        private static boolean afterHandover(Command command, ProcessingState state) {
            return command == Command.REPORT_ISSUE
                    && (state == ProcessingState.READY_FOR_DELIVERY || state == ProcessingState.DELIVERED);
        }

        private static boolean resumeWithNothingOpen(Command command, ProcessingState state, int openIssues) {
            return resumeWithIssuesOpen(command, state) && openIssues == 0;
        }

        private static boolean resumeWithIssuesOpen(Command command, ProcessingState state) {
            return command == Command.RESUME_HOLD && state == ProcessingState.ON_HOLD;
        }
    }

    /**
     * Decides one hold command.
     *
     * @param command    the command the caller is applying
     * @param orderState the processing state the command lands on
     * @param openIssues the issues that stay open after the command's own closes
     * @return the verdict to apply
     */
    static Verdict decide(Command command, ProcessingState orderState, int openIssues) {
        return new HoldDecision(command, orderState, openIssues).decide();
    }

    Verdict decide() {
        Verdict verdict = Rule.H7.verdict();
        for (Rule rule : Rule.values()) {
            if (rule.appliesTo(command, orderState, openIssues)) {
                verdict = rule.verdict();
                break;
            }
        }
        return verdict;
    }
}
