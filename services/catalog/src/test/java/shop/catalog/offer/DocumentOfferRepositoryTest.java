package shop.catalog.offer;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import shop.catalog.IntegrationTest;
import shop.catalog.JsonAssert;
import shop.catalog.RecordedEvents;
import shop.catalog.offer.Offer.State;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static shop.catalog.TestTransaction.transactional;

/**
 * The document-with-history adapter, one round trip per shape the schema can lose: the draft surface with its media, a frozen
 * version with its media and its {@code copiedFrom}, the availability date, the state and the absent identifiers. The write is
 * committed in its own transaction and read in another, so the read leaves Hibernate's first-level cache behind.
 */
@IntegrationTest
class DocumentOfferRepositoryTest {

    @Autowired
    DocumentOfferRepository repository;
    @Autowired
    RecordedEvents recorded;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    DocumentOfferRepository.OfferHistory history;

    @Test
    void savesAndReadsADraftWithItsMedia() {
        OfferEditor saved = OfferDocumentFixture.draftOfferWithMedia();
        transactional(() -> repository.save(saved));
        OfferEditor read = transactional(() -> repository.get(saved.offerId)).orElseThrow();

        assertThat(read.toSurface()).isEqualTo(saved.toSurface());
        assertThat(read.toSurface().media()).extracting(Media::url)
                .containsExactlyElementsOf(locationsOfInstanceA());
        OfferAssert.assertThat(read).hasState(State.DRAFT).hasVersion(0).hasNoAvailabilityStart();
    }

    /** RULE-2a: the location is part of the stored media array, so the JSONB round trip returns the same value. */
    @Test
    void keepsTheLocationOfEveryMediaItemTheDraftCarries() {
        OfferEditor saved = OfferDocumentFixture.draftOfferWithMedia();
        transactional(() -> repository.save(saved));

        assertThat(contentOf(saved.offerId)).contains("https://cdn.example/M-01.jpg");
        assertThat(transactional(() -> repository.get(saved.offerId)).orElseThrow().toSurface().media())
                .extracting(Media::url)
                .containsExactlyElementsOf(locationsOfInstanceA());
    }

    @Test
    void savesAndReadsAnOfferUnderReview() {
        OfferEditor saved = OfferDocumentFixture.offerPendingReview();
        transactional(() -> repository.save(saved));
        OfferEditor read = transactional(() -> repository.get(saved.offerId)).orElseThrow();

        OfferAssert.assertThat(read)
                .hasState(State.PENDING_REVIEW)
                .hasVersion(0)
                .hasAvailabilityStart(OfferFixture.AVAILABILITY_START);
    }

    @Test
    void savesAndReadsAPublishedOfferWithItsFrozenVersion() {
        OfferEditor saved = OfferDocumentFixture.publishedOffer();
        transactional(() -> repository.save(saved));
        OfferEditor read = transactional(() -> repository.get(saved.offerId)).orElseThrow();

        assertThat(read.version(1)).isEqualTo(saved.version(1));
        assertThat(read.version(1).media()).extracting(Media::url).containsExactlyElementsOf(locationsOfInstanceA());
        OfferAssert.assertThat(read)
                .hasState(State.PUBLISHED)
                .hasVersion(1)
                .hasAvailabilityStart(OfferFixture.AVAILABILITY_START);
    }

    @Test
    void keepsARevisionOfTheDraftOutOfTheFrozenVersion() {
        OfferEditor saved = OfferDocumentFixture.publishedOffer();
        saved.updateDescription(OfferFixture.aDifferentDraft(), OfferFixture.freeIdentifiers());
        transactional(() -> repository.save(saved));
        OfferEditor read = transactional(() -> repository.get(saved.offerId)).orElseThrow();

        assertThat(read.version(1)).isEqualTo(saved.version(1));
        assertThat(read.toSurface().description().blocks()).isEqualTo(OfferFixture.aDifferentDraft().blocks());
    }

    @Test
    void keepsAnOlderVersionWhenARevertAppendsTheNextOne() {
        OfferEditor saved = OfferDocumentFixture.publishedOffer();
        saved.revert(1, OfferFixture.now(), OfferFixture.REVIEWER, OfferFixture.freeIdentifiers());
        transactional(() -> repository.save(saved));
        OfferEditor read = transactional(() -> repository.get(saved.offerId)).orElseThrow();

        assertThat(read.version(1)).isEqualTo(saved.version(1));
        assertThat(read.version(2)).isEqualTo(saved.version(2));
        assertThat(read.version(2).copiedFrom()).isEqualTo(1);
    }

    @Test
    void remembersARemovedOffer() {
        OfferEditor saved = OfferDocumentFixture.removedOffer();
        transactional(() -> repository.save(saved));
        OfferEditor read = transactional(() -> repository.get(saved.offerId)).orElseThrow();

        OfferAssert.assertThat(read).hasState(State.REMOVED).hasVersion(1);
    }

    @Test
    void storesAnIdentifierTheDraftDoesNotCarryAsAbsent() {
        String blankProductNumber = "  ";
        OfferEditor first = OfferDocumentFixture.draftOffer(OfferDocumentFixture.uniqueOfferId(), blankProductNumber, null);
        OfferEditor second = OfferDocumentFixture.draftOffer(OfferDocumentFixture.uniqueOfferId(), blankProductNumber, null);
        transactional(() -> repository.save(first));
        transactional(() -> repository.save(second));

        assertThat(productNumberOf(second.offerId)).isNull();
        assertThat(transactional(() -> repository.get(second.offerId))).isPresent();
    }

    @Test
    void publishesTheSnapshotOnceAndEveryEmittedEventOnce() {
        OfferEditor saved = OfferDocumentFixture.publishedOffer();
        transactional(() -> repository.save(saved));

        String offerId = saved.offerId;
        assertThat(recorded.of(Offer.class)).containsExactly(saved.toOffer());
        assertThat(recorded.of(OfferEvent.class)).containsExactly(
                new OfferEvent.OfferDraftUpdated(offerId),
                new OfferEvent.OfferDraftUpdated(offerId),
                new OfferEvent.OfferDraftUpdated(offerId),
                new OfferEvent.OfferReviewRequested(offerId),
                OfferFixture.versionFrozen(offerId, 1, saved.version(1).description()));
    }

    @Test
    void publishesNothingAndWritesNoRowForASaveThatChangedNothing() {
        OfferEditor saved = OfferDocumentFixture.publishedOffer();
        transactional(() -> repository.save(saved));
        int lockAfterFirstSave = lockOf(saved.offerId);
        String contentAfterFirstSave = contentOf(saved.offerId);
        transactional(() -> repository.save(saved));

        assertThat(recorded.of(Offer.class)).hasSize(1);
        assertThat(recorded.of(OfferEvent.class)).hasSize(5);
        assertThat(contentOf(saved.offerId)).isEqualTo(contentAfterFirstSave);
        assertThat(lockOf(saved.offerId)).isEqualTo(lockAfterFirstSave);
    }

    @Test
    void recordsEveryEmittedEventInTheHistory() {
        OfferEditor saved = OfferDocumentFixture.publishedOffer();
        transactional(() -> repository.save(saved));
        List<String> historyTypes = transactional(() -> history.findByOfferIdOrderById(saved.offerId)).stream()
                .map(DocumentOfferRepository.OfferHistoryEntity::eventType)
                .toList();

        assertThat(historyTypes).containsExactly("OfferDraftUpdated", "OfferDraftUpdated", "OfferDraftUpdated",
                "OfferReviewRequested", "OfferVersionFrozen");
    }

    @Test
    void storesTheFrozenEventAsReadableJson() {
        OfferEditor saved = OfferDocumentFixture.publishedOffer();
        transactional(() -> repository.save(saved));

        JsonAssert.assertThat(payloadOf(saved.offerId, "OfferVersionFrozen")).hasFieldsLike("""
                { "offerId": "%s", "offerVersion": 1,
                  "description": { "identifiers": { "productNumber": "%s" } } }
                """.formatted(saved.offerId, saved.toSurface().description().identifiers().productNumber()));
    }

    @Test
    void refusesAnIdentifierAnotherLiveOfferCarries() {
        String productNumber = OfferDocumentFixture.uniqueProductNumber();
        String eanGtin = OfferDocumentFixture.uniqueEanGtin();
        OfferEditor first = OfferDocumentFixture.draftOffer(OfferDocumentFixture.uniqueOfferId(), productNumber, eanGtin);
        OfferEditor second = OfferDocumentFixture.draftOffer(OfferDocumentFixture.uniqueOfferId(), productNumber, eanGtin);
        transactional(() -> repository.save(first));

        assertThatThrownBy(() -> transactional(() -> repository.save(second)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void refusesANewOfferTheIdentifiersOfARemovedOne() {
        String productNumber = OfferDocumentFixture.uniqueProductNumber();
        String eanGtin = OfferDocumentFixture.uniqueEanGtin();
        transactional(() -> repository.save(
                OfferDocumentFixture.removedOffer(OfferDocumentFixture.uniqueOfferId(), productNumber, eanGtin)));
        OfferEditor successor = OfferDocumentFixture.draftOffer(OfferDocumentFixture.uniqueOfferId(), productNumber, eanGtin);

        assertThatThrownBy(() -> transactional(() -> repository.save(successor)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void readsNothingForAnUnknownOffer() {
        Optional<OfferEditor> read = transactional(() -> repository.get(OfferDocumentFixture.uniqueOfferId()));

        assertThat(read).isEmpty();
    }

    /** RULE-2a: a document that predates `url` has no location to read, so the migration refuses it (0003-offer-document-media-url). */
    @Test
    void refusesAStoredDocumentWhoseMediaCarriesNoLocation() {
        String offerId = OfferDocumentFixture.uniqueOfferId();
        jdbc.update("insert into offer_document (offer_id, version, state, content) values (?, 1, 'DRAFT', ?::jsonb)",
                offerId, """
                        { "description": { "title": "LEGO Star Wars 75423",
                                           "identifiers": { "productNumber": "75423" },
                                           "brand": { "name": "LEGO", "verified": true },
                                           "condition": "NEW", "categoryPath": ["Zabawki"],
                                           "blocks": [ { "paragraph": { "text": "Opis" } } ] },
                          "media": [ { "mediaId": "M-01", "kind": "IMAGE", "position": 1, "primary": true,
                                       "format": "jpg" } ] }
                        """);

        assertThatThrownBy(() -> repository.get(offerId))
                .hasMessageContaining("a media item needs its location");
    }

    private int lockOf(String offerId) {
        return jdbc.queryForObject("select version from offer_document where offer_id = ?", Integer.class, offerId);
    }

    private String contentOf(String offerId) {
        return jdbc.queryForObject("select content::text from offer_document where offer_id = ?", String.class, offerId);
    }

    private String productNumberOf(String offerId) {
        return jdbc.queryForObject("select product_number from offer_document where offer_id = ?", String.class, offerId);
    }

    private String payloadOf(String offerId, String eventType) {
        return jdbc.queryForObject("select payload::text from offer_history where offer_id = ? and event_type = ?",
                String.class, offerId, eventType);
    }

    private static List<String> locationsOfInstanceA() {
        return OfferFixture.mediaOfInstanceA().stream().map(Media::url).toList();
    }
}
