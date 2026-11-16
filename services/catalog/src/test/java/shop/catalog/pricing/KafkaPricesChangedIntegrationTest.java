package shop.catalog.pricing;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import shop.catalog.IntegrationTest;
import shop.catalog.KafkaEvents;
import shop.catalog.RecordedEvents;
import shop.catalog.tools.JsonConfiguration;
import tools.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static shop.catalog.TestTransaction.transactional;

/**
 * The edge for the second producing context (13 D28): an activation through {@link PricingService} — the fact the RULE-9 poll drives —
 * reaches the broker with the active price, the currency it is expressed in and the entry's own {@code effectiveFrom}. The scheduling
 * test is the other half of S2: on the production save path no activation fact exists at all, so nothing can leave the process.
 */
@IntegrationTest
class KafkaPricesChangedIntegrationTest {

    @Autowired
    PricingService pricing;
    @Autowired
    RecordedEvents recorded;
    @Value("${spring.kafka.bootstrap-servers}")
    String bootstrapServers;
    @Value("${catalog.events.topic}")
    String topic;

    @Test
    void sendsTheActivatedPriceOnTheEventsTopicKeyedByTheOffer() {
        String offerId = PricingFixture.OFFER_ID + "-" + UUID.randomUUID();
        Instant validFrom = Instant.now().plusSeconds(60);
        transactional(() -> pricing.schedulePrice(offerId,
                PricingFixture.openPriceChange("P-1", PricingFixture.pln(29900), validFrom)));
        transactional(() -> pricing.activateDue(offerId, validFrom));

        ConsumerRecord<String, String> record = KafkaEvents.recordAwaiting(bootstrapServers, topic, offerId);

        assertThat(record.key()).isEqualTo(offerId);
        JsonNode shipped = JsonConfiguration.OBJECT_MAPPER.readTree(record.value());
        assertThat(shipped.get("eventType").asString()).isEqualTo(PricesChanged.TYPE);
        assertThat(shipped.get("key").asString()).isEqualTo(offerId);
        assertThat(Instant.parse(shipped.get("occurredAt").asString())).isEqualTo(validFrom);
        JsonNode payload = shipped.get("payload");
        assertThat(payload.get("productId").asString()).isEqualTo(offerId);
        assertThat(payload.get("currency").asString()).isEqualTo(PricingFixture.PLN);
        assertThat(payload.get("price").get("amountMinor").asInt()).isEqualTo(29900);
        assertThat(payload.get("price").get("scale").asInt()).isEqualTo(2);
        assertThat(Instant.parse(payload.get("effectiveFrom").asString())).isEqualTo(validFrom);
        assertThat(payload.get("discounts").isEmpty()).isTrue();
    }

    @Test
    void recordsNoActivationWhenAPriceIsOnlyScheduled() {
        String offerId = PricingFixture.OFFER_ID + "-" + UUID.randomUUID();

        transactional(() -> pricing.schedulePrice(offerId,
                PricingFixture.openPriceChange("P-1", PricingFixture.pln(29900), Instant.now().plusSeconds(60))));

        assertThat(recorded.of(PricingEvent.PriceScheduled.class))
                .extracting(PricingEvent.PriceScheduled::offerId).contains(offerId);
        assertThat(recorded.of(PricingEvent.PriceActivated.class))
                .extracting(PricingEvent.PriceActivated::offerId).doesNotContain(offerId);
    }
}
