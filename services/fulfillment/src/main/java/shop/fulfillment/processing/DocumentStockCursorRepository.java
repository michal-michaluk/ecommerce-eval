package shop.fulfillment.processing;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

import shop.fulfillment.kernel.StockCursor;

/**
 * The {@link StockCursorRepository} adapter: one row, one watermark.
 *
 * <p>The row is written after a page was read and published, so a poll that fails leaves the watermark where it was and
 * the next run reads the same window again — the read is idempotent, and re-reading is what the port promises rather than
 * a retry policy of ours (element 13 RULE-6, RULE-7).
 */
@Repository
class DocumentStockCursorRepository implements StockCursorRepository {

    private static final String STOCK_CHANGES = "stock-changes";

    private final Cursors cursors;

    DocumentStockCursorRepository(Cursors cursors) {
        this.cursors = cursors;
    }

    @Override
    public Optional<StockCursor> read() {
        return cursors.findById(STOCK_CHANGES).map(StockCursorEntity::toCursor);
    }

    @Override
    public void write(StockCursor cursor) {
        cursors.save(cursors.findById(STOCK_CHANGES)
                .orElseGet(() -> new StockCursorEntity(STOCK_CHANGES))
                .set(cursor));
    }

    interface Cursors extends JpaRepository<StockCursorEntity, String> {
    }

    @Entity
    @Table(name = "warehouse_stock_cursor")
    static class StockCursorEntity {

        @Id
        @Column(name = "name")
        private String name;
        @Column(name = "cursor")
        private String cursor;

        protected StockCursorEntity() {
        }

        StockCursorEntity(String name) {
            this.name = name;
        }

        StockCursor toCursor() {
            return new StockCursor(cursor);
        }

        StockCursorEntity set(StockCursor cursor) {
            this.cursor = cursor.value();
            return this;
        }
    }
}
