package shop.fulfillment.kernel;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The processing values of element 02: one position per order line whose state never regresses, the issue record whose only
 * mutable fact is the close, and the state machine's two derived edges.
 */
class ProcessingValuesTest {

    @Test
    void movesAPositionFromPendingToPickedToPacked() {
        assertThat(ProcessingFixture.aPosition().state()).isEqualTo(PositionState.PENDING);
        assertThat(ProcessingFixture.aPosition().picked().state()).isEqualTo(PositionState.PICKED);
        assertThat(ProcessingFixture.aPosition().picked().packed().state()).isEqualTo(PositionState.PACKED);
    }

    @Test
    void keepsTheLineWhenThePositionMoves() {
        OrderPosition picked = ProcessingFixture.aPosition("99881", 3).picked();

        assertThat(picked.productNumber()).isEqualTo("99881");
        assertThat(picked.quantity()).isEqualTo(3);
    }

    @Test
    void refusesAPositionWithoutAProductNumber() {
        assertThatThrownBy(() -> ProcessingFixture.aPositionAs(" ", "name", 1, PositionState.PENDING))
                .isInstanceOf(FulfillmentException.class);
    }

    @Test
    void refusesAPositionWithANonPositiveQuantity() {
        assertThatThrownBy(() -> ProcessingFixture.aPositionAs("75423", "name", 0, PositionState.PENDING))
                .isInstanceOf(FulfillmentException.class);
    }

    @Test
    void refusesAPositionWithoutAState() {
        assertThatThrownBy(() -> ProcessingFixture.aPositionAs("75423", "name", 1, null))
                .hasMessageContaining("positions.state");
    }

    @Test
    void anIssueIsOpenUntilTheResumeNamesIt() {
        assertThat(ProcessingFixture.anIssue().isOpen()).isTrue();
        assertThat(ProcessingFixture.anIssue().close(ProcessingFixture.REPORTED_AT).isOpen()).isFalse();
    }

    @Test
    void refusesAnIssueWithoutTheProductItIsAbout() {
        assertThatThrownBy(() -> FulfillmentIssue.builder()
                .issueId(ProcessingFixture.ISSUE_ID)
                .processingId(ProcessingFixture.PROCESSING_ID)
                .kind(IssueKinds.LOST)
                .reportedBy(ProcessingFixture.EMPLOYEE)
                .reportedAt(ProcessingFixture.REPORTED_AT)
                .build())
                .hasMessageContaining("productNumber");
    }

    @Test
    void refusesAnIssueWithoutAKind() {
        assertThatThrownBy(() -> FulfillmentIssue.builder()
                .issueId(ProcessingFixture.ISSUE_ID)
                .processingId(ProcessingFixture.PROCESSING_ID)
                .productNumber("75423")
                .reportedBy(ProcessingFixture.EMPLOYEE)
                .reportedAt(ProcessingFixture.REPORTED_AT)
                .build())
                .hasMessageContaining("kind");
    }

    @Test
    void refusesAnIssueWithoutItsReporter() {
        assertThatThrownBy(() -> FulfillmentIssue.builder()
                .issueId(ProcessingFixture.ISSUE_ID)
                .processingId(ProcessingFixture.PROCESSING_ID)
                .kind(IssueKinds.LOST)
                .productNumber("75423")
                .reportedAt(ProcessingFixture.REPORTED_AT)
                .build())
                .hasMessageContaining("reportedBy");
    }

    @Test
    void theFirstConfirmationStartsPickingAndNothingElseMoves() {
        assertThat(ProcessingState.ASSIGNED.afterPicking()).isEqualTo(ProcessingState.PICKING);
        assertThat(ProcessingState.PICKING.afterPicking()).isEqualTo(ProcessingState.PICKING);
        assertThat(ProcessingState.PENDING.afterPicking()).isEqualTo(ProcessingState.PENDING);
    }

    @Test
    void theLastPackedPositionPacksTheOrder() {
        assertThat(ProcessingState.PICKING.afterPacking(true)).isEqualTo(ProcessingState.PACKED);
        assertThat(ProcessingState.PICKING.afterPacking(false)).isEqualTo(ProcessingState.PICKING);
    }

    @ParameterizedTest
    @EnumSource(ProcessingState.class)
    void everyStateNamesItselfOnTheWire(ProcessingState state) {
        assertThat(state.wireName()).matches("[a-z][A-Za-z]*");
    }

    @Test
    void theWireNamesAreTheOnesTheContractUses() {
        assertThat(ProcessingState.READY_FOR_DELIVERY.wireName()).isEqualTo("readyForDelivery");
        assertThat(ProcessingState.ON_HOLD.wireName()).isEqualTo("onHold");
        assertThat(ProcessingState.PENDING.wireName()).isEqualTo("pending");
    }

    @Test
    void eachLegNamesTheStateItReaches() {
        assertThat(Leg.L1.legState()).isEqualTo(LegState.PARCEL_REGISTERED);
        assertThat(Leg.L6.legState()).isEqualTo(LegState.DELIVERED_TO_CUSTOMER);
        assertThat(List.of(Leg.values())).hasSize(LegState.values().length);
    }

    @ParameterizedTest
    @EnumSource(LegState.class)
    void everyLegStateNamesItselfOnTheWire(LegState state) {
        assertThat(state.wireName()).matches("[a-z][A-Za-z]*");
    }

    @Test
    void theLegStateNamesAreTheOnesTheElementExamplesUse() {
        assertThat(LegState.PARCEL_REGISTERED.wireName()).isEqualTo("parcelRegistered");
        assertThat(LegState.PARCEL_READY.wireName()).isEqualTo("parcelReady");
        assertThat(LegState.DELIVERED_TO_CUSTOMER.wireName()).isEqualTo("deliveredToCustomer");
    }
}
