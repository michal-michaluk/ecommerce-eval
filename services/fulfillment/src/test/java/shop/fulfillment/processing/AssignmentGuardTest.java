package shop.fulfillment.processing;

import org.junit.jupiter.api.Test;

import shop.fulfillment.kernel.ProcessingState;

import static org.assertj.core.api.Assertions.assertThat;
import static shop.fulfillment.kernel.ProcessingFixture.EMPLOYEE;
import static shop.fulfillment.kernel.ProcessingFixture.OTHER_EMPLOYEE;

/**
 * The decision table of element 07, row by row: exclusivity is granted, refused or a no-op, and the states after picking are
 * past a claim whatever the assignee says.
 */
class AssignmentGuardTest {

    @Test
    void grantsAnUnclaimedPendingOrderToAnEmployee() {
        assertThat(AssignmentGuard.decide(ProcessingState.PENDING, null, EMPLOYEE))
                .isEqualTo(new AssignmentGuard.Verdict.Grant());
    }

    @Test
    void refusesAClaimOfAnActorWhoIsNotAnEmployee() {
        assertThat(AssignmentGuard.decide(ProcessingState.PENDING, null, null))
                .isEqualTo(new AssignmentGuard.Verdict.Refuse(AssignmentGuard.Reason.ACTOR));
    }

    @Test
    void refusesAForeignClaimWhileTheOrderIsWorked() {
        assertThat(AssignmentGuard.decide(ProcessingState.PICKING, EMPLOYEE, OTHER_EMPLOYEE))
                .isEqualTo(new AssignmentGuard.Verdict.Refuse(AssignmentGuard.Reason.ALREADY_ASSIGNED));
    }

    @Test
    void refusesAForeignClaimOnAHeldOrderExactlyAsOnAnUnheldOne() {
        assertThat(AssignmentGuard.decide(ProcessingState.ON_HOLD, EMPLOYEE, OTHER_EMPLOYEE))
                .isEqualTo(new AssignmentGuard.Verdict.Refuse(AssignmentGuard.Reason.ALREADY_ASSIGNED));
    }

    @Test
    void answersARepeatedClaimByTheOwnerAsANoOp() {
        assertThat(AssignmentGuard.decide(ProcessingState.PACKED, EMPLOYEE, EMPLOYEE))
                .isEqualTo(new AssignmentGuard.Verdict.NoOp());
    }

    @Test
    void refusesAClaimOnAReadyOrderEvenForItsOwner() {
        assertThat(AssignmentGuard.decide(ProcessingState.READY_FOR_DELIVERY, EMPLOYEE, EMPLOYEE))
                .isEqualTo(new AssignmentGuard.Verdict.Refuse(AssignmentGuard.Reason.PAST_PICKING));
    }

    @Test
    void refusesAClaimOnADeliveredOrder() {
        assertThat(AssignmentGuard.decide(ProcessingState.DELIVERED, OTHER_EMPLOYEE, EMPLOYEE))
                .isEqualTo(new AssignmentGuard.Verdict.Refuse(AssignmentGuard.Reason.CLOSED));
    }

    @Test
    void refusesAForeignClaimOnAReadyOrderAsPastPickingRatherThanAlreadyAssigned() {
        assertThat(AssignmentGuard.decide(ProcessingState.READY_FOR_DELIVERY, EMPLOYEE, OTHER_EMPLOYEE))
                .isEqualTo(new AssignmentGuard.Verdict.Refuse(AssignmentGuard.Reason.PAST_PICKING));
    }
}
