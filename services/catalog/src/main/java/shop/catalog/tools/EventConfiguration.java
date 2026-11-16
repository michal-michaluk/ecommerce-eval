package shop.catalog.tools;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.Map;

/**
 * The producer behind the one edge that leaves the process (13, D28): a JSON envelope on one topic, keyed by the offer id, with the
 * delivery guarantee written into the factory rather than left to a deployment — {@code acks=all} plus idempotent produce is what
 * "at-least-once, consumers deduplicate on {@code eventId}" costs on the producer side.
 *
 * <p>A template of its own, because the value type is the envelope and nothing else in the service produces: a second producer to
 * configure would be a second transport, which {@code integration.single-transport} forbids.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(EventSettings.class)
class EventConfiguration {

    @Bean
    KafkaTemplate<String, EventEnvelope> catalogEventsTemplate(EventSettings settings) {
        DefaultKafkaProducerFactory<String, EventEnvelope> producer = new DefaultKafkaProducerFactory<>(
                Map.of(
                        ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, settings.bootstrapServers(),
                        ProducerConfig.ACKS_CONFIG, "all",
                        ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true),
                new StringSerializer(),
                new EventEnvelopeSerializer());
        return new KafkaTemplate<>(producer);
    }
}
