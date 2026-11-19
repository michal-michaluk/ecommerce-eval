package shop.fulfillment.processing;

import org.junit.jupiter.api.Test;

import shop.fulfillment.kernel.ProcessingState;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The hold table of element 09, row by row: an issue never becomes a state of its own, a later issue joins the hold it needs
 * no second one, and only a resume that leaves nothing open lifts it.
 */
class HoldDecisionTest {

    @Test
    void placesTheHoldOnTheFirstIssueOfPickingOrder() {
        assertThat(HoldDecision.decide(HoldDecision.Command.REPORT_ISSUE, ProcessingState.PICKING, 1))
                .isEqualTo(new HoldDecision.Verdict.Grant());
    }

    @Test
    void placesTheHoldOnTheFirstIssueOfAPackedOrder() {
        assertThat(HoldDecision.decide(HoldDecision.Command.REPORT_ISSUE, ProcessingState.PACKED, 1))
                .isEqualTo(new HoldDecision.Verdict.Grant());
    }

    @Test
    void joinsTheHoldForAFurtherIssue() {
        assertThat(HoldDecision.decide(HoldDecision.Command.REPORT_ISSUE, ProcessingState.ON_HOLD, 1))
                .isEqualTo(new HoldDecision.Verdict.NoOp());
    }

    @Test
    void refusesAnIssueBeforePickingStarted() {
        assertThat(HoldDecision.decide(HoldDecision.Command.REPORT_ISSUE, ProcessingState.ASSIGNED, 0))
                .isEqualTo(new HoldDecision.Verdict.Refuse(HoldDecision.Reason.NOT_STARTED));
    }

    @Test
    void refusesAnIssueOnAnUnclaimedOrder() {
        assertThat(HoldDecision.decide(HoldDecision.Command.REPORT_ISSUE, ProcessingState.PENDING, 0))
                .isEqualTo(new HoldDecision.Verdict.Refuse(HoldDecision.Reason.NOT_STARTED));
    }

    @Test
    void refusesAnIssueAfterTheOrderLeftTheWarehouse() {
        assertThat(HoldDecision.decide(HoldDecision.Command.REPORT_ISSUE, ProcessingState.READY_FOR_DELIVERY, 0))
                .isEqualTo(new HoldDecision.Verdict.Refuse(HoldDecision.Reason.PAST_HANDOVER));
        assertThat(HoldDecision.decide(HoldDecision.Command.REPORT_ISSUE, ProcessingState.DELIVERED, 0))
                .isEqualTo(new HoldDecision.Verdict.Refuse(HoldDecision.Reason.PAST_HANDOVER));
    }

    @Test
    void liftsTheHoldOnceNothingStaysOpen() {
        assertThat(HoldDecision.decide(HoldDecision.Command.RESUME_HOLD, ProcessingState.ON_HOLD, 0))
                .isEqualTo(new HoldDecision.Verdict.Grant());
    }

    @Test
    void refusesAResumeThatLeavesAnIssueOpen() {
        assertThat(HoldDecision.decide(HoldDecision.Command.RESUME_HOLD, ProcessingState.ON_HOLD, 1))
                .isEqualTo(new HoldDecision.Verdict.Refuse(HoldDecision.Reason.ISSUES_REMAIN));
    }

    @Test
    void answersAResumeOfAnOrderThatIsNotHeldAsANoOp() {
        assertThat(HoldDecision.decide(HoldDecision.Command.RESUME_HOLD, ProcessingState.PICKING, 0))
                .isEqualTo(new HoldDecision.Verdict.NoOp());
    }
}
