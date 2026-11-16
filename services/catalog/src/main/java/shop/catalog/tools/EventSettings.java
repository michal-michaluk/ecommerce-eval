package shop.catalog.tools;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The configurable half of the outbound edge (13, D28): the topic the three public types travel on, the brokers to send them to, and the
 * payload variant. {@code bootstrap-servers} defaults to the existing Spring Kafka key, so a deployment configures the broker once, in
 * the place it already does ({@code spring.kafka.bootstrap-servers}).
 *
 * <p>{@link Payload} is the {@code catalog.events.payload} variant 13 documents: {@code snapshot} is the default because the board offers
 * no reason to split, {@code thin} exists for a consumer that prefers to fetch the content itself.
 */
@ConfigurationProperties(prefix = "catalog.events")
public record EventSettings(String topic, String bootstrapServers, Payload payload) {

    static final String DEFAULT_TOPIC = "catalog.offer.events.v1";

    public EventSettings {
        topic = topic == null || topic.isBlank() ? DEFAULT_TOPIC : topic;
        bootstrapServers = bootstrapServers == null || bootstrapServers.isBlank() ? "localhost:9092" : bootstrapServers;
        payload = payload == null ? Payload.SNAPSHOT : payload;
    }

    public enum Payload {
        SNAPSHOT,
        THIN
    }
}
