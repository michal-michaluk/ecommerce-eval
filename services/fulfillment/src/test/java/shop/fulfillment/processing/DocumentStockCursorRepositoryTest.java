package shop.fulfillment.processing;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import shop.fulfillment.IntegrationTest;
import shop.fulfillment.kernel.StockCursor;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The stock-change watermark as the row that survives a restart: one value, written after a page was published, read back
 * by the next poll.
 */
@IntegrationTest
class DocumentStockCursorRepositoryTest {

    @Autowired
    DocumentStockCursorRepository cursors;

    @Test
    void answersTheWatermarkItWasGiven() {
        cursors.write(new StockCursor("2026-11-19T10:00:00Z"));

        assertThat(cursors.read()).contains(new StockCursor("2026-11-19T10:00:00Z"));
    }

    @Test
    void movesTheOneWatermarkInsteadOfAddingASecondOne() {
        cursors.write(new StockCursor("2026-11-19T10:00:00Z"));
        cursors.write(new StockCursor("2026-11-19T11:00:00Z"));

        assertThat(cursors.read()).contains(new StockCursor("2026-11-19T11:00:00Z"));
    }

    @Test
    void answersNothingBeforeTheFirstPoll() {
        assertThat(cursors.read()).isEmpty();
    }
}
