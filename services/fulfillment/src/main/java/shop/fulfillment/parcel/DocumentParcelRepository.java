package shop.fulfillment.parcel;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Clock;
import java.time.Year;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import shop.fulfillment.kernel.DomainEvent;
import shop.fulfillment.kernel.LabelDocument;
import shop.fulfillment.kernel.LegState;
import shop.fulfillment.kernel.ParcelId;

/**
 * The {@link ParcelRepository} document-snapshot adapter: one JSONB row per parcel holding the aggregate's own snapshot,
 * the two columns a query reads without opening the document (the processing it carries, and the leg it reached), and one
 * row per stored label.
 *
 * <p>The label is a row of its own rather than a field of the snapshot: the carrier's printed bytes are the document we
 * serve back, not state of the parcel, and a rehydrated parcel — the status poll does that once a tick — must not carry a
 * page of PDF around to answer a question it does not ask ({@code 06-consignment-note.md} RULE-9).
 *
 * <p>The adapter drains the aggregate's event buffer before writing, so a failed write cannot publish a fact twice, and
 * publishes every event once after the row is written ({@code persistence.drain-events}, {@code persistence.publish-after-write}).
 * Publication happens inside the caller's transaction, so a registration that rolls back announces no delivery number.
 */
@Repository
class DocumentParcelRepository implements ParcelRepository {

    private static final String MINTED_ID = "PCL-%d-%04d";

    private final ParcelRows parcels;
    private final LabelRows labels;
    private final ApplicationEventPublisher publisher;
    private final Clock clock;

    DocumentParcelRepository(ParcelRows parcels, LabelRows labels, ApplicationEventPublisher publisher, Clock clock) {
        this.parcels = parcels;
        this.labels = labels;
        this.publisher = publisher;
        this.clock = clock;
    }

    @Override
    public Optional<ParcelEditor> get(ParcelId parcelId) {
        return parcels.findById(parcelId.value()).map(DocumentParcelRepository::rehydrate);
    }

    @Override
    public Optional<ParcelEditor> findByProcessing(String processingId) {
        return parcels.findByProcessingId(processingId).map(DocumentParcelRepository::rehydrate);
    }

    @Override
    public List<ParcelEditor> awaitingDelivery() {
        return parcels.findAllByLegStateNot(LegState.DELIVERED_TO_CUSTOMER.name()).stream()
                .map(DocumentParcelRepository::rehydrate)
                .toList();
    }

    @Override
    public ParcelId nextIdentity() {
        long number = Optional.ofNullable(parcels.nextIdentity()).orElse(1L);
        return new ParcelId(MINTED_ID.formatted(Year.now(clock).getValue(), number));
    }

    @Override
    public void save(ParcelEditor parcel) {
        List<DomainEvent> emitted = eventsEmittedFrom(parcel);
        parcels.save(stored(parcel.toSnapshot()));
        emitted.forEach(event -> publisher.publishEvent(event));
    }

    @Override
    public Optional<LabelDocument> labelOf(ParcelId parcelId) {
        return labels.findById(parcelId.value()).map(ParcelLabelEntity::toDocument);
    }

    @Override
    public void storeLabel(ParcelId parcelId, LabelDocument label) {
        labels.save(labels.findById(parcelId.value())
                .orElseGet(() -> new ParcelLabelEntity(parcelId.value()))
                .set(label));
    }

    /** Copies the aggregate's buffer and clears it before the write, so a failed save cannot publish a fact twice. */
    static List<DomainEvent> eventsEmittedFrom(ParcelEditor parcel) {
        List<DomainEvent> emitted = List.copyOf(parcel.events);
        parcel.events.clear();
        return emitted;
    }

    /**
     * The aggregate rebuilt from its snapshot: the constructor is the all-args one on purpose, and every list is rehydrated
     * as a mutable one so a loaded aggregate behaves exactly like a freshly registered one.
     */
    private static ParcelEditor rehydrate(ParcelEntity entity) {
        Parcel snapshot = entity.snapshot();
        return new ParcelEditor(snapshot.parcelId(), new ArrayList<>(), snapshot.processingId(),
                snapshot.reference(), snapshot.weightGrams(), snapshot.serviceCode(), snapshot.sequence(),
                snapshot.carrierIdentity(), new ArrayList<>(snapshot.legs()),
                new ArrayList<>(snapshot.unmappedPayloads()), snapshot.legState(), snapshot.collectionHandle());
    }

    private ParcelEntity stored(Parcel snapshot) {
        return parcels.findById(snapshot.parcelId().value())
                .orElseGet(() -> new ParcelEntity(snapshot.parcelId().value()))
                .setSnapshot(snapshot);
    }

    interface ParcelRows extends JpaRepository<ParcelEntity, String> {

        Optional<ParcelEntity> findByProcessingId(String processingId);

        List<ParcelEntity> findAllByLegStateNot(String legState);

        @Query(value = "select nextval('parcel_id_seq')", nativeQuery = true)
        Long nextIdentity();
    }

    interface LabelRows extends JpaRepository<ParcelLabelEntity, String> {
    }

    @Entity
    @Table(name = "parcel")
    static class ParcelEntity {

        @Id
        @Column(name = "parcel_id")
        private String parcelId;
        @Version
        private long version;
        @Column(name = "processing_id")
        private String processingId;
        @Column(name = "leg_state")
        private String legState;
        @JdbcTypeCode(SqlTypes.JSON)
        private Parcel snapshot;

        protected ParcelEntity() {
        }

        ParcelEntity(String parcelId) {
            this.parcelId = parcelId;
        }

        Parcel snapshot() {
            return snapshot;
        }

        ParcelEntity setSnapshot(Parcel snapshot) {
            this.snapshot = snapshot;
            this.processingId = snapshot.processingId().value();
            this.legState = snapshot.legState().name();
            return this;
        }
    }

    @Entity
    @Table(name = "parcel_label")
    static class ParcelLabelEntity {

        @Id
        @Column(name = "parcel_id")
        private String parcelId;
        @Column(name = "format")
        private String format;
        @Column(name = "document")
        private byte[] document;

        protected ParcelLabelEntity() {
        }

        ParcelLabelEntity(String parcelId) {
            this.parcelId = parcelId;
        }

        LabelDocument toDocument() {
            return new LabelDocument(document, format);
        }

        ParcelLabelEntity set(LabelDocument label) {
            this.format = label.format();
            this.document = label.bytes();
            return this;
        }
    }
}
