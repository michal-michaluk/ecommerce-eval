package shop.catalog.offer;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import shop.catalog.IntegrationTest;
import shop.catalog.KafkaEvents;
import shop.catalog.tools.JsonConfiguration;
import tools.jackson.databind.JsonNode;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static shop.catalog.TestTransaction.transactional;

/**
 * The one test that proves the edge is real (13 D28, "tests" row): a publication through the offer context's own service reaches the
 * Kafka container on the configured topic, keyed by the offer id, carrying the envelope and the snapshot of 13. Nothing is mocked — the
 * adapter, the producer and the broker are the production ones, and the hash is recomputed from the bytes on the topic the way a consumer
 * would (RULE-10, D39).
 */
@IntegrationTest
class KafkaOfferPublishedIntegrationTest {

    @Autowired
    OfferService offers;
    @Value("${spring.kafka.bootstrap-servers}")
    String bootstrapServers;
    @Value("${catalog.events.topic}")
    String topic;

    @Test
    void sendsTheFrozenVersionOnTheEventsTopicKeyedByTheOffer() {
        String offerId = OfferDocumentFixture.uniqueOfferId();
        String eanGtin = publishAnOffer(offerId);

        ConsumerRecord<String, String> record = KafkaEvents.recordAwaiting(bootstrapServers, topic, offerId);

        assertThat(record.topic()).isEqualTo(topic);
        assertThat(record.key()).isEqualTo(offerId);
        JsonNode shipped = JsonConfiguration.OBJECT_MAPPER.readTree(record.value());
        assertThat(shipped.get("eventType").asString()).isEqualTo(OfferPublished.TYPE);
        assertThat(shipped.get("eventVersion").asInt()).isEqualTo(1);
        assertThat(shipped.get("key").asString()).isEqualTo(offerId);
        assertThat(UUID.fromString(shipped.get("eventId").asString())).isNotNull();
        JsonNode payload = shipped.get("payload");
        assertThat(payload.get("productId").asString()).isEqualTo(offerId);
        assertThat(payload.get("offerVersion").asInt()).isEqualTo(1);
        assertThat(payload.get("identifiers").get("eanGtin").asString()).isEqualTo(eanGtin);
        assertThat(payload.get("title").asString()).isEqualTo(ProductDescriptionFixture.INSTANCE_A_TITLE);
        assertThat(payload.get("media").size()).isEqualTo(3);
        assertThat(payload.get("media").get(0).get("url").asString())
                .isEqualTo(OfferFixture.image("M-01", 1, true).url());
        assertThat(payload.get("availabilityStart").asString()).isEqualTo(OfferFixture.AVAILABILITY_START.toString());
        assertThat(recomputedHashOf(payload)).isEqualTo(payload.get("descriptionHash").asString());
    }

    /** What a consumer does with the record it received: drop identity and the hash, hash the value, compare. */
    private static String recomputedHashOf(JsonNode payload) {
        OfferPublished.Value value = JsonConfiguration.OBJECT_MAPPER.readValue(payload.toString(), OfferPublished.Value.class);
        return DescriptionHash.of(JsonConfiguration.OBJECT_MAPPER.writeValueAsString(value));
    }

    private String publishAnOffer(String offerId) {
        String eanGtin = OfferDocumentFixture.uniqueEanGtin();
        transactional(() -> offers.draftOffer(offerId, ProductDescriptionDraftFixture.draftWithIdentifiers(
                OfferDocumentFixture.uniqueProductNumber(), eanGtin)));
        transactional(() -> offers.updateMedia(offerId, OfferFixture.mediaOfInstanceA()));
        transactional(() -> offers.scheduleAvailability(offerId, OfferFixture.AVAILABILITY_START));
        transactional(() -> offers.publish(offerId, OfferFixture.PUBLISHED_AT, OfferFixture.REVIEWER));
        return eanGtin;
    }
}
