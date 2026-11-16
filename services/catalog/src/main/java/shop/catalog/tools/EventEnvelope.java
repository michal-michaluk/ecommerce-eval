package shop.catalog.tools;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * The wire format every outbound record travels in (13, D28): the idempotency key a consumer deduplicates on, the public type's name
 * and version, the instant the fact happened, the partition key and the payload itself.
 *
 * <p>It is the envelope of the broker contract, not of an in-process event: inside the process the record type <em>is</em> the
 * notification ({@code integration.no-envelope}), and only the one edge that leaves the process needs a uniform head. {@code key} is
 * the offer id, so one offer's records stay in publication order on one partition (13 RULE-4).
 *
 * <p>{@code eventId} is new per publication, which is what makes delivery at-least-once safe: a redelivered record carries the id the
 * consumer already saw and is dropped by it (13 RULE-5).
 */
public record EventEnvelope(UUID eventId, String eventType, int eventVersion, Instant occurredAt, String key, Object payload) {

    public static final int VERSION = 1;

    public EventEnvelope {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(eventType, "eventType");
        Objects.requireNonNull(occurredAt, "occurredAt");
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(payload, "payload");
    }

    /** The envelope of one publication: a fresh {@code eventId}, the contract's version and the payload's own key. */
    public static EventEnvelope of(String eventType, Instant occurredAt, String key, Object payload) {
        return new EventEnvelope(UUID.randomUUID(), eventType, VERSION, occurredAt, key, payload);
    }
}
