package shop.fulfillment.processing;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

import shop.fulfillment.kernel.DomainEvent;
import shop.fulfillment.kernel.StockChange;
import shop.fulfillment.kernel.StockChangePage;
import shop.fulfillment.kernel.StockCursor;
import shop.fulfillment.kernel.WarehouseStock;

/**
 * The stock-change read of element 13 as one scheduled job: read what changed after our watermark, raise one in-process
 * {@code stock changed} per observation, then move the watermark.
 *
 * <p>The warehouse cannot notify us — it has no outbound subscription — so this is the only way the board event of
 * `eventstorming.json:134` can happen at all, and it is a fact we raise rather than one we receive (RULE-6). The read is
 * idempotent, so re-reading an overlapping window costs nothing; the watermark is written only after the page was
 * published, so a failed poll leaves it where it was. The same observation arriving twice in one page is collapsed before
 * it is published: a duplicate is not a second change.
 *
 * <p>Failure is surfaced, never smoothed: no retry, no backoff and no fallback is invented here, the scheduled task's own
 * error handler logs the failure and the next tick reads the same window again (RULE-7). A deployment with no warehouse
 * adapter has nothing to poll and does nothing.
 *
 * <p>The interval is {@code fulfillment.warehouse.poll-interval}, default {@value #DEFAULT_POLL_INTERVAL} — a number no
 * source states (element 13 Q-13-1's cadence sibling), chosen fast enough that a stale pick list is a minute old and slow
 * enough that one instance is not a load on the warehouse.
 */
@Component
class StockChangePollingIntegration {

    static final String DEFAULT_POLL_INTERVAL = "60s";

    private final Optional<WarehouseStock> warehouse;
    private final StockCursorRepository cursors;
    private final ApplicationEventPublisher publisher;
    private final Clock clock;

    StockChangePollingIntegration(Optional<WarehouseStock> warehouse, StockCursorRepository cursors,
                                  ApplicationEventPublisher publisher, Clock clock) {
        this.warehouse = warehouse;
        this.cursors = cursors;
        this.publisher = publisher;
        this.clock = clock;
    }

    /**
     * One poll: the page after our watermark, published observation by observation.
     */
    @Scheduled(fixedDelayString = "${fulfillment.warehouse.poll-interval:" + DEFAULT_POLL_INTERVAL + "}",
            initialDelayString = "${fulfillment.warehouse.poll-interval:" + DEFAULT_POLL_INTERVAL + "}")
    @Transactional
    void poll() {
        warehouse.ifPresent(this::read);
    }

    private void read(WarehouseStock stock) {
        StockChangePage page = stock.stockChanges(watermark());
        page.changes().stream().distinct().forEach(this::publish);
        cursors.write(page.nextCursor());
    }

    /**
     * Where this boundary reads from: the stored watermark, or — on the very first poll — now. A fresh deployment does not
     * replay the warehouse's whole history, and no source asks it to.
     */
    private StockCursor watermark() {
        return cursors.read().orElseGet(() -> new StockCursor(Instant.now(clock).toString()));
    }

    private void publish(StockChange observation) {
        publisher.publishEvent(new DomainEvent.StockChanged(observation.productNumber(), observation.location(),
                observation.available(), observation.reserved(), observation.observedAt()));
    }
}
