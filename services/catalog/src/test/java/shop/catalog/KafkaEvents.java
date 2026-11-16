package shop.catalog;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Reads what the service actually shipped: one record with one key, straight from the broker, as the shopping side would read it. The
 * consumer is created per call with a group of its own and {@code earliest}, so a record published before the read is still found, and
 * the bounded wait is a poll loop rather than a sleep.
 */
public class KafkaEvents {

    private static final Duration WAITING = Duration.ofSeconds(30);
    private static final Duration POLLING = Duration.ofSeconds(1);

    public static ConsumerRecord<String, String> recordAwaiting(String bootstrapServers, String topic, String key) {
        try (KafkaConsumer<String, String> consumer = consumerOf(bootstrapServers)) {
            consumer.subscribe(List.of(topic));
            Instant deadline = Instant.now().plus(WAITING);
            while (Instant.now().isBefore(deadline)) {
                ConsumerRecord<String, String> found = matching(consumer.poll(POLLING), key);
                if (found != null) {
                    return found;
                }
            }
        }
        throw new AssertionError("no record keyed " + key + " arrived on " + topic + " within " + WAITING);
    }

    private static ConsumerRecord<String, String> matching(Iterable<ConsumerRecord<String, String>> records, String key) {
        for (ConsumerRecord<String, String> record : records) {
            if (key.equals(record.key())) {
                return record;
            }
        }
        return null;
    }

    private static KafkaConsumer<String, String> consumerOf(String bootstrapServers) {
        Map<String, Object> properties = new HashMap<>();
        properties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        properties.put(ConsumerConfig.GROUP_ID_CONFIG, "catalog-events-" + UUID.randomUUID());
        properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        return new KafkaConsumer<>(properties);
    }
}
