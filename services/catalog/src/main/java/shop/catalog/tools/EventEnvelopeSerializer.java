package shop.catalog.tools;

import org.apache.kafka.common.serialization.Serializer;
import tools.jackson.databind.ObjectMapper;

/**
 * Writes an {@link EventEnvelope} with the one process mapper ({@code json.single-mapper}), so the bytes on the topic are the bytes
 * the JSONB columns and the tests see. A serializer of its own would be a second JSON dialect on the same values — and the payload's
 * package-private records need the mapper's field-and-creator visibility to be written at all.
 */
public class EventEnvelopeSerializer implements Serializer<EventEnvelope> {

    private static final ObjectMapper MAPPER = JsonConfiguration.OBJECT_MAPPER;

    @Override
    public byte[] serialize(String topic, EventEnvelope event) {
        return event == null ? null : MAPPER.writeValueAsBytes(event);
    }
}
