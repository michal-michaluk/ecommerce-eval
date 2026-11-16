package shop.catalog.offer;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.ColumnTransformer;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Primary;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import shop.catalog.offer.Offer.State;
import shop.catalog.tools.JsonConfiguration;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * The {@link OfferRepository} adapter, document plus history: one JSONB row per offer holding the draft surface, one immutable row
 * per frozen version with its media, and one row per emitted domain event.
 *
 * <p>A frozen version is written once — a publication inserts the next number and a revert inserts another one (RULE-5) — so the
 * rows carry no optimistic version and are never updated. The document row does: {@code @Version} turns a lost update into a
 * failure instead of a silent overwrite.
 *
 * <p>The adapter drains {@link OfferEditor#events} before writing, then publishes the {@link Offer} snapshot once for a changed save
 * and each {@link OfferEvent} once, inside the caller's transaction, after the rows are written.
 *
 * <p>The JSON is written with a mapper derived from the one process-wide mapper, because the draft and the frozen description embed
 * two sealed value hierarchies ({@code Parameter.Value}, {@code DescriptionBlock}) that carry no {@code @JsonTypeInfo} of their own —
 * a domain type does not name its own wire format. The derived mapper adds that discriminator and nothing else, and it is declared
 * here because only this adapter stores those values.
 */
@Primary
@Repository
class DocumentOfferRepository implements OfferRepository {

    private static final ObjectMapper DOCUMENT = JsonConfiguration.OBJECT_MAPPER.rebuild()
            .addMixIn(ProductDescription.Parameter.Value.class, PolymorphicValue.class)
            .addMixIn(ProductDescription.DescriptionBlock.class, PolymorphicBlock.class)
            .build();

    private final OfferDocuments documents;
    private final OfferVersions versions;
    private final OfferVersionMedia media;
    private final OfferHistory history;
    private final ApplicationEventPublisher publisher;
    private final Clock clock;

    DocumentOfferRepository(OfferDocuments documents, OfferVersions versions, OfferVersionMedia media, OfferHistory history,
                            ApplicationEventPublisher publisher, Clock clock) {
        this.documents = documents;
        this.versions = versions;
        this.media = media;
        this.history = history;
        this.publisher = publisher;
        this.clock = clock;
    }

    @Override
    public Optional<OfferEditor> get(String offerId) {
        return documents.findById(offerId).map(this::rehydrate);
    }

    @Override
    public void save(OfferEditor offer) {
        List<OfferEvent> emitted = eventsEmittedFrom(offer);
        Offer snapshot = offer.toOffer();
        documents.save(documentOf(offer, snapshot));
        appendFrozenVersions(offer, snapshot.version());
        emitted.forEach(event -> history.save(new OfferHistoryEntity(offer.offerId, Instant.now(clock), event)));
        if (!emitted.isEmpty()) {
            publisher.publishEvent(snapshot);
        }
        emitted.forEach(event -> publisher.publishEvent(event));
    }

    /** Copies the aggregate's buffer and clears it before the write, so a failed save cannot publish a fact twice. */
    static List<OfferEvent> eventsEmittedFrom(OfferEditor offer) {
        List<OfferEvent> emitted = List.copyOf(offer.events);
        offer.events.clear();
        return emitted;
    }

    private OfferEditor rehydrate(OfferDocumentEntity entity) {
        OfferSurface surface = read(entity.content(), OfferSurface.class);
        List<OfferVersion> frozen = frozenVersions(entity.offerId());
        return new OfferEditor(entity.offerId(), new ArrayList<>(), surface.description(), surface.media(),
                entity.availabilityStart(), entity.state(), frozen);
    }

    /**
     * The frozen versions as a mutable list: the aggregate appends to it on a publication or a revert, and a rehydrated editor must
     * behave exactly like a freshly drafted one.
     */
    private List<OfferVersion> frozenVersions(String offerId) {
        Map<Integer, List<Media>> mediaOfVersion = media.findByOfferIdOrderByVersionNumberAscMediaPositionAsc(offerId).stream()
                .collect(Collectors.groupingBy(OfferVersionMediaEntity::versionNumber,
                        Collectors.mapping(OfferVersionMediaEntity::toMedia, Collectors.toList())));
        return new ArrayList<>(versions.findByOfferIdOrderByVersionNumber(offerId).stream()
                .map(frozen -> frozen.toVersion(mediaOfVersion.getOrDefault(frozen.versionNumber(), List.of())))
                .toList());
    }

    private OfferDocumentEntity documentOf(OfferEditor offer, Offer snapshot) {
        OfferSurface surface = offer.toSurface();
        OfferDocumentEntity document = documents.findById(offer.offerId)
                .orElseGet(() -> new OfferDocumentEntity(offer.offerId));
        return document.setSurface(surface, snapshot, write(surface));
    }

    private void appendFrozenVersions(OfferEditor offer, int currentVersion) {
        int persisted = versions.findFirstByOfferIdOrderByVersionNumberDesc(offer.offerId)
                .map(OfferVersionEntity::versionNumber)
                .orElse(0);
        for (int number = persisted + 1; number <= currentVersion; number++) {
            OfferVersion frozen = offer.version(number);
            versions.save(new OfferVersionEntity(offer.offerId, frozen, write(frozen.description())));
            media.saveAll(OfferVersionMediaEntity.frozenBy(offer.offerId, frozen));
        }
    }

    /** The stored form is the rendered JSON: two trees built on either side of a round trip are not equal, their rendering is. */
    private static String write(Object value) {
        return DOCUMENT.writeValueAsString(value);
    }

    private static <T> T read(String json, Class<T> type) {
        return DOCUMENT.readValue(json, type);
    }

    /** The discriminator the two sealed value hierarchies need, and the only extension the derived mapper makes. */
    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.WRAPPER_OBJECT)
    interface PolymorphicValue {
    }

    /** @see PolymorphicValue */
    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.WRAPPER_OBJECT)
    interface PolymorphicBlock {
    }

    interface OfferDocuments extends JpaRepository<OfferDocumentEntity, String> {
    }

    interface OfferVersions extends JpaRepository<OfferVersionEntity, Long> {

        List<OfferVersionEntity> findByOfferIdOrderByVersionNumber(String offerId);

        Optional<OfferVersionEntity> findFirstByOfferIdOrderByVersionNumberDesc(String offerId);
    }

    interface OfferVersionMedia extends JpaRepository<OfferVersionMediaEntity, Long> {

        List<OfferVersionMediaEntity> findByOfferIdOrderByVersionNumberAscMediaPositionAsc(String offerId);
    }

    interface OfferHistory extends JpaRepository<OfferHistoryEntity, Long> {

        List<OfferHistoryEntity> findByOfferIdOrderById(String offerId);
    }

    @Entity
    @Table(name = "offer_document")
    static class OfferDocumentEntity {

        @Id
        @Column(name = "offer_id")
        private String offerId;
        @Version
        private long version;
        @Enumerated(EnumType.STRING)
        private State state;
        @Column(name = "product_number")
        private String productNumber;
        @Column(name = "ean_gtin")
        private String eanGtin;
        @Column(name = "availability_start")
        private LocalDate availabilityStart;
        @Column(name = "content")
        @ColumnTransformer(read = "content::text", write = "?::jsonb")
        private String content;

        protected OfferDocumentEntity() {
        }

        OfferDocumentEntity(String offerId) {
            this.offerId = offerId;
        }

        String offerId() {
            return offerId;
        }

        State state() {
            return state;
        }

        LocalDate availabilityStart() {
            return availabilityStart;
        }

        String content() {
            return content;
        }

        OfferDocumentEntity setSurface(OfferSurface surface, Offer snapshot, String document) {
            ProductDescription.Identifiers identifiers = surface.description().identifiers();
            this.state = snapshot.state();
            this.availabilityStart = snapshot.availabilityStart();
            this.productNumber = identifier(identifiers == null ? null : identifiers.productNumber());
            this.eanGtin = identifier(identifiers == null ? null : identifiers.eanGtin());
            assignContent(surface, document);
            return this;
        }

        /**
         * Writes the drafting content only when it changed: the database canonicalises the rendering of a {@code jsonb} value, so the text
         * read back never equals the text written, and a command that emits nothing would otherwise rewrite the row and bump its
         * {@code version}.
         */
        private void assignContent(OfferSurface surface, String document) {
            if (content == null || !surface.equals(read(content, OfferSurface.class))) {
                this.content = document;
            }
        }

        /** An identifier the draft does not carry is absent, not empty, so it neither collides nor reads as a value. */
        private static String identifier(String value) {
            return value == null || value.isBlank() ? null : value;
        }
    }

    @Entity
    @Table(name = "offer_version")
    static class OfferVersionEntity {

        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;
        @Column(name = "offer_id")
        private String offerId;
        @Column(name = "version_number")
        private int versionNumber;
        @Column(name = "description")
        @ColumnTransformer(read = "description::text", write = "?::jsonb")
        private String description;
        @Column(name = "availability_start")
        private LocalDate availabilityStart;
        @Column(name = "published_at")
        private Instant publishedAt;
        @Column(name = "published_by")
        private String publishedBy;
        @Column(name = "copied_from")
        private Integer copiedFrom;

        protected OfferVersionEntity() {
        }

        OfferVersionEntity(String offerId, OfferVersion frozen, String description) {
            this.offerId = offerId;
            this.versionNumber = frozen.number();
            this.description = description;
            this.availabilityStart = frozen.availabilityStart();
            this.publishedAt = frozen.publishedAt();
            this.publishedBy = frozen.publishedBy();
            this.copiedFrom = frozen.copiedFrom();
        }

        int versionNumber() {
            return versionNumber;
        }

        OfferVersion toVersion(List<Media> media) {
            return new OfferVersion(versionNumber, read(description, ProductDescription.class), media, availabilityStart,
                    publishedAt, publishedBy, copiedFrom);
        }
    }

    @Entity
    @Table(name = "offer_version_media")
    static class OfferVersionMediaEntity {

        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;
        @Column(name = "offer_id")
        private String offerId;
        @Column(name = "version_number")
        private int versionNumber;
        @Column(name = "media_position")
        private int mediaPosition;
        @Column(name = "media_id")
        private String mediaId;
        @Enumerated(EnumType.STRING)
        private Media.Kind kind;
        @Column(name = "is_primary")
        private boolean primaryMedia;
        @Column(name = "media_format")
        private String mediaFormat;
        @Column(name = "url")
        private String url;

        protected OfferVersionMediaEntity() {
        }

        private OfferVersionMediaEntity(String offerId, int versionNumber, Media source) {
            this.offerId = offerId;
            this.versionNumber = versionNumber;
            this.mediaPosition = source.position();
            this.mediaId = source.mediaId();
            this.kind = source.kind();
            this.primaryMedia = source.primary();
            this.mediaFormat = source.format();
            this.url = source.url();
        }

        static List<OfferVersionMediaEntity> frozenBy(String offerId, OfferVersion frozen) {
            return frozen.media().stream()
                    .map(source -> new OfferVersionMediaEntity(offerId, frozen.number(), source))
                    .toList();
        }

        int versionNumber() {
            return versionNumber;
        }

        Media toMedia() {
            return new Media(mediaId, kind, mediaPosition, primaryMedia, mediaFormat, url);
        }
    }

    @Entity
    @Table(name = "offer_history")
    static class OfferHistoryEntity {

        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;
        @Column(name = "offer_id")
        private String offerId;
        @Column(name = "recorded_at")
        private Instant recordedAt;
        @Column(name = "event_type")
        private String eventType;
        @Column(name = "payload")
        @ColumnTransformer(read = "payload::text", write = "?::jsonb")
        private String payload;

        protected OfferHistoryEntity() {
        }

        OfferHistoryEntity(String offerId, Instant recordedAt, OfferEvent event) {
            this.offerId = offerId;
            this.recordedAt = recordedAt;
            this.eventType = event.getClass().getSimpleName();
            this.payload = write(event);
        }

        String eventType() {
            return eventType;
        }
    }
}
