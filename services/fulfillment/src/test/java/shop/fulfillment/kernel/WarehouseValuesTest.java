package shop.fulfillment.kernel;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The warehouse vocabulary of element 13: a product, an opaque location, the identity we mint for one allocation, one
 * picked item, the issue note it answers, and the two independent sums of what a location holds.
 */
class WarehouseValuesTest {

    @Test
    void acceptsAProductNumberAndRefusesABlankOne() {
        assertThat(new ProductId("75423").value()).isEqualTo("75423");
        assertThatThrownBy(() -> new ProductId(" ")).isInstanceOf(FulfillmentException.class);
    }

    @Test
    void ordersTwoLocationsByTheirOpaqueCode() {
        assertThat(new LocationCode("A-01-01")).isLessThan(new LocationCode("B-02-02"));
    }

    @Test
    void refusesABlankLocationCode() {
        assertThatThrownBy(() -> new LocationCode(null))
                .isInstanceOfSatisfying(FulfillmentException.class, refused -> assertThat(refused.error())
                        .isEqualTo(FulfillmentException.Error.LOCATION_CODE_MALFORMED));
    }

    @Test
    void refusesAMissingReservationId() {
        assertThatThrownBy(() -> new ReservationId(" ")).hasMessageContaining("reservationId");
    }

    @Test
    void holdsFreeAndAllocatedUnitsAsTwoIndependentSums() {
        assertThat(WarehouseFixture.stock(10, 2).available()).isEqualTo(10);
        assertThat(WarehouseFixture.stock(10, 2).reserved()).isEqualTo(2);
    }

    @Test
    void refusesNegativeStockAtALocation() {
        assertThatThrownBy(() -> WarehouseFixture.stock(-1, 0)).isInstanceOf(FulfillmentException.class);
        assertThatThrownBy(() -> WarehouseFixture.stock(0, -1)).isInstanceOf(FulfillmentException.class);
    }

    @Test
    void holdsOnePickedItemPerScan() {
        assertThat(WarehouseFixture.aPickedItem().quantity()).isEqualTo(1);
    }

    @Test
    void refusesAPickedItemWithoutItsQuantity() {
        assertThatThrownBy(() -> WarehouseFixture.aPickedItemOf(new ProductId("75423"), WarehouseFixture.A_SHELF, 0))
                .isInstanceOfSatisfying(FulfillmentException.class, refused -> assertThat(refused.error())
                        .isEqualTo(FulfillmentException.Error.QUANTITY_NOT_POSITIVE));
    }

    @Test
    void holdsTheGoodsIssueNoteOfOneReservation() {
        assertThat(WarehouseFixture.anIssueNote().documentNumber()).isEqualTo("WZ-2026-0001");
    }

    @Test
    void refusesAnIssueNoteWithoutLines() {
        assertThatThrownBy(() -> WarehouseFixture.anIssueNoteOf("WZ-2026-0001", null, List.of()))
                .isInstanceOfSatisfying(FulfillmentException.class, refused -> assertThat(refused.error())
                        .isEqualTo(FulfillmentException.Error.COMMAND_VALUE_MISSING));
    }

    @Test
    void refusesAnIssueNoteWithoutADocumentNumber() {
        assertThatThrownBy(() -> WarehouseFixture.anIssueNoteOf(" ", null, List.of(WarehouseFixture.aPickedItem())))
                .isInstanceOf(FulfillmentException.class);
    }

    @Test
    void holdsOneObservedStockChange() {
        assertThat(WarehouseFixture.aStockChange().available()).isEqualTo(10);
    }

    @Test
    void refusesAStockChangeWithoutAnObservationTime() {
        assertThatThrownBy(() -> StockChange.builder()
                .productNumber(new ProductId("75423"))
                .location(WarehouseFixture.A_SHELF)
                .build())
                .isInstanceOf(FulfillmentException.class);
    }

    @Test
    void holdsTheWatermarkOfTheNextStockRead() {
        assertThat(WarehouseFixture.anEmptyPage().nextCursor().value()).isEqualTo("0");
        assertThat(WarehouseFixture.anEmptyPage().changes()).isEmpty();
    }

    @Test
    void refusesAStockChangePageWithoutItsCursor() {
        assertThatThrownBy(() -> new StockChangePage(null, List.of())).isInstanceOf(FulfillmentException.class);
    }
}
