package shop.catalog.tools;

import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutionException;

/**
 * The one outbound edge (13, D28): it sends the envelope and waits for the broker's acknowledgement, because a record the broker never
 * accepted must fail the write that produced it — a published offer without its event is exactly the state 13 forbids. The wait is
 * the `acks=all` wait of the configured producer, so the caller's transaction is still open while it happens and rolls back on failure
 * (`integration.synchronous-in-transaction`).
 *
 * <p>The topic and the brokers are configuration; the key is the payload's own offer id, so one offer's records stay ordered
 * (RULE-4).
 */
@Component
@RequiredArgsConstructor
public class EventStream {

    private final KafkaTemplate<String, EventEnvelope> catalogEvents;
    private final EventSettings settings;

    public void publish(EventEnvelope event) {
        try {
            catalogEvents.send(settings.topic(), event.key(), event).get();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while publishing " + event.eventType() + " " + event.eventId(), interrupted);
        } catch (ExecutionException refused) {
            throw new IllegalStateException("cannot publish " + event.eventType() + " " + event.eventId()
                    + " for " + event.key(), refused.getCause());
        }
    }
}
