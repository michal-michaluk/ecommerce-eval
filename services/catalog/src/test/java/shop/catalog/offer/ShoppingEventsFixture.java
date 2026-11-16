package shop.catalog.offer;

import org.intellij.lang.annotations.Language;
import shop.catalog.tools.EventSettings;
import shop.catalog.tools.EventStream;
import shop.catalog.tools.JsonConfiguration;

import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The payloads 13 promises, written as the JSON a consumer reads — strict comparison against these literals is how a missing or an extra
 * field becomes a failure. The hash is the one part a test cannot write down from the sample page, so it is interpolated and asserted
 * separately: {@link #hashOfShippedPayload} is what a consumer does with the bytes it received, which is the property RULE-10 promises.
 */
public class ShoppingEventsFixture {

    public static final String EDITED_TITLE = "LEGO Star Wars X-Play, wydanie drugie";

    public static OfferIntegration integrationOf(OfferRepository offers, EventStream events) {
        return new OfferIntegration(offers, events, settings(EventSettings.Payload.SNAPSHOT), OfferFixture.fixedClock());
    }

    public static OfferIntegration thinIntegrationOf(OfferRepository offers, EventStream events) {
        return new OfferIntegration(offers, events, settings(EventSettings.Payload.THIN), OfferFixture.fixedClock());
    }

    private static EventSettings settings(EventSettings.Payload payload) {
        return new EventSettings("catalog.offer.events.v1", "localhost:9092", payload);
    }

    /** The hash a consumer recomputes from the payload it received: drop identity and the hash itself, hash the rest (RULE-10, D39). */
    public static String hashOfShippedPayload(Object payload) {
        String received = JsonConfiguration.OBJECT_MAPPER.writeValueAsString(payload);
        OfferPublished.Value value = JsonConfiguration.OBJECT_MAPPER.readValue(received, OfferPublished.Value.class);
        return DescriptionHash.of(JsonConfiguration.OBJECT_MAPPER.writeValueAsString(value));
    }

    @Language("JSON")
    public static String publishedJson(int offerVersion, String descriptionHash) {
        return """
                {
                  "productId": "PRD-75423",
                  "offerVersion": %d,
                  "descriptionHash": "%s",
                  "identifiers": {
                    "productNumber": "75423",
                    "eanGtin": "5702017817705",
                    "setName": "SMART Play X-Wing Czerwona Piątka Luke'a"
                  },
                  "availabilityStart": "2026-07-01",
                  "title": "LEGO Star Wars SMART Play X-Wing Czerwona Piątka Luke'a 75423",
                  "brand": { "name": "LEGO", "verified": true },
                  "condition": "new",
                  "categoryPath": ["Dziecko", "Zabawki", "Klocki", "LEGO", "Zestawy", "Star Wars"],
                  "parameters": [
                    { "code": "childAge", "label": "Wiek dziecka", "value": { "from": 6, "unit": "lat" },
                      "valueType": "range_from" },
                    { "code": "pieceCount", "label": "Liczba elementów", "value": 581, "valueType": "integer" },
                    { "code": "gender", "label": "Płeć", "value": ["boys", "girls", "unisex"], "valueType": "enum_set" },
                    { "code": "certificates", "label": "Certyfikaty, opinie, atesty", "value": ["CE", "EN 71"],
                      "valueType": "list" },
                    { "code": "safetyInfo", "label": "Informacje dot. bezpieczeństwa i zgodności produktu",
                      "value": "Nie nadaje się dla dzieci w wieku poniżej 36 miesięcy", "valueType": "text" }
                  ],
                  "description": {
                    "blocks": [
                      { "type": "heading", "level": 3,
                        "text": "LEGO Star Wars SMART Play X-Wing Czerwona Piątka Luke'a 75423" },
                      { "type": "paragraph",
                        "text": "Odkryj nową galaktykę LEGO® Star Wars™ dzięki interaktywnej zabawce konstrukcyjnej …" },
                      { "type": "bullet_list",
                        "items": ["Interaktywna zabawka konstrukcyjna ożywia X-winga…", "SMART klocek umożliwia…"] }
                    ]
                  },
                  "media": [
                    { "id": "M-01", "kind": "image", "position": 1, "primary": true, "format": "jpg",
                      "url": "https://cdn.example/M-01.jpg" },
                    { "id": "M-02", "kind": "image", "position": 2, "primary": false, "format": "jpg",
                      "url": "https://cdn.example/M-02.jpg" },
                    { "id": "M-03", "kind": "image", "position": 3, "primary": false, "format": "jpg",
                      "url": "https://cdn.example/M-03.jpg" }
                  ]
                }
                """.formatted(offerVersion, descriptionHash);
    }

    /** The {@code thin} variant of 13: ids and version only, for a consumer that fetches the content itself. */
    @Language("JSON")
    public static String thinJson() {
        return """
                { "productId": "PRD-75423", "offerVersion": 1 }
                """;
    }

    @Language("JSON")
    public static String removedJson(Instant removedAt) {
        return """
                { "productId": "PRD-75423", "removedAt": "%s" }
                """.formatted(removedAt);
    }

    public static String hashOfInstanceA() {
        return DescriptionHash.of(JsonConfiguration.OBJECT_MAPPER.writeValueAsString(
                OfferPublished.valueOf(OfferFixture.versionWithNumber(1))));
    }

    /** The same frozen content with one component changed, so a hash test can tell the two apart. */
    public static String hashOf(String title, List<Media> media, LocalDate availabilityStart) {
        ProductDescription description = ProductDescriptionDraftFixture.draftWithTitle(title).toDefinition();
        OfferVersion version = versionWith(description, media, availabilityStart);
        return DescriptionHash.of(JsonConfiguration.OBJECT_MAPPER.writeValueAsString(OfferPublished.valueOf(version)));
    }

    public static OfferVersion versionWith(ProductDescription description, List<Media> media, LocalDate availabilityStart) {
        return new OfferVersion(1, description, media, availabilityStart, OfferFixture.PUBLISHED_AT,
                OfferFixture.REVIEWER, null);
    }

    public static OfferEditor publishedOffer() {
        OfferEditor offer = OfferFixture.publishedOffer();
        DocumentOfferRepository.eventsEmittedFrom(offer);
        return offer;
    }

    /** Version 1 as published, then two more versions of edited content, so a revert can reproduce the first with the next number. */
    public static OfferEditor offerAtVersionThree() {
        OfferEditor offer = publishedOffer();
        offer.updateDescription(ProductDescriptionDraftFixture.draftWithTitle(EDITED_TITLE), OfferFixture.freeIdentifiers());
        offer.publish(OfferFixture.now(), OfferFixture.REVIEWER, OfferFixture.freeIdentifiers());
        offer.publish(OfferFixture.now(), OfferFixture.REVIEWER, OfferFixture.freeIdentifiers());
        DocumentOfferRepository.eventsEmittedFrom(offer);
        return offer;
    }

    public static OfferEditor removedOffer() {
        OfferEditor offer = OfferFixture.removedOffer();
        DocumentOfferRepository.eventsEmittedFrom(offer);
        return offer;
    }

    /** The offer aggregate the adapter reads the frozen version back from, without a database — it drains like the real adapter. */
    public static final class Offers implements OfferRepository {

        private final Map<String, OfferEditor> offers = new HashMap<>();

        @Override
        public Optional<OfferEditor> get(String offerId) {
            return Optional.ofNullable(offers.get(offerId));
        }

        @Override
        public void save(OfferEditor offer) {
            DocumentOfferRepository.eventsEmittedFrom(offer);
            offers.put(offer.offerId, offer);
        }
    }
}
