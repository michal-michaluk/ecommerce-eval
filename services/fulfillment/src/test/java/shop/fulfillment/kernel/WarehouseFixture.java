package shop.fulfillment.kernel;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * The warehouse values the tests work with: what a location holds, what a scan confirmed and the note a goods issue
 * answers.
 */
public class WarehouseFixture {

    public static final LocationCode A_SHELF = new LocationCode("A-01-01");

    private WarehouseFixture() {
    }

    public static StockAtLocation stock(int available, int reserved) {
        return new StockAtLocation(A_SHELF, available, reserved);
    }

    public static StockAtLocation stockAt(String code, int available) {
        return new StockAtLocation(new LocationCode(code), available, 0);
    }

    public static StockAtLocation stock(String code, int available, int reserved) {
        return new StockAtLocation(new LocationCode(code), available, reserved);
    }

    public static PickedItem aPickedItem() {
        return aPickedItemOf(new ProductId(OrderFixture.PRODUCT_NUMBER), A_SHELF, 1);
    }

    public static PickedItem aPickedItemOf(ProductId productNumber, LocationCode location, int quantity) {
        return new PickedItem(productNumber, location, quantity);
    }

    public static IssueNote anIssueNote() {
        return anIssueNoteOf("WZ-2026-0001", LocalDate.parse("2026-11-19"), List.of(aPickedItem()));
    }

    public static IssueNote anIssueNoteOf(String documentNumber, LocalDate date, List<PickedItem> lines) {
        return new IssueNote(documentNumber, date, lines);
    }

    public static StockChange aStockChange() {
        return StockChange.builder()
                .productNumber(new ProductId(OrderFixture.PRODUCT_NUMBER))
                .location(A_SHELF)
                .available(10)
                .reserved(2)
                .observedAt(Instant.parse("2026-11-19T09:31:12Z"))
                .build();
    }

    public static StockChangePage anEmptyPage() {
        return new StockChangePage(new StockCursor("0"), List.of());
    }
}
