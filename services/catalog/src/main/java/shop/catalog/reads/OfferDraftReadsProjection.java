package shop.catalog.reads;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import org.hibernate.annotations.ColumnTransformer;
import org.springframework.context.event.EventListener;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import shop.catalog.offer.Media;
import shop.catalog.offer.Offer;
import shop.catalog.offer.ProductDescription;
import shop.catalog.tools.JsonConfiguration;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * The draft read model of 14, built from the offer snapshot and answered in one row.
 *
 * <p>Its edge is the {@link Offer} snapshot: the offer's adapter publishes it inside the writing transaction after the rows are
 * written, so a request that reads back through this projection cannot lag behind its own {@code PATCH} reply (17 RULE-5,
 * {@code integration.listener-joins-transaction}). The handler copies the payload into this context's own table — the draft in
 * builder form and its media, one JSON document, because nothing queries into it and every read loads it whole.
 *
 * <p>The two answers that are not stored are the ones that are not facts about the last write: the findings are asked of 08 when the
 * screen is served (a stored report would drift from the rules and their configuration), and visibility is asked of 11, which is the
 * only place that owns the clock (17 RULE-6).
 */
@Component
@Transactional
@AllArgsConstructor
class OfferDraftReadsProjection {

    private static final ObjectMapper DOCUMENT = JsonConfiguration.OBJECT_MAPPER.rebuild()
            .addMixIn(ProductDescription.Parameter.Value.class, PolymorphicValue.class)
            .addMixIn(ProductDescription.DescriptionBlock.class, PolymorphicBlock.class)
            .build();

    private final OfferDraftRows rows;
    private final DraftFindings findings;
    private final OfferVisibility visibility;

    @EventListener
    public void handle(Offer offer) {
        OfferDraftRow row = rows.findById(offer.offerId()).orElseGet(() -> new OfferDraftRow(offer.offerId()));
        rows.save(row.copyOf(offer, write(new DraftDocument(offer.description(), offer.media()))));
    }

    /** The whole draft screen of one offer, or empty when no offer was ever written under that identity. */
    @Transactional(readOnly = true)
    public Optional<OfferDraftReads> findDraft(String offerId) {
        return rows.findById(offerId)
                .map(row -> OfferDraftReads.ofNullable(row, qualityOf(row),
                        visibility.visibilityOf(row.state(), row.availabilityStart())));
    }

    private List<QualityFinding> qualityOf(OfferDraftRow row) {
        ProductDescription.Draft description = row.description();
        return description == null ? List.of() : findings.findingsOf(description, row.media());
    }

    private static String write(Object value) {
        return DOCUMENT.writeValueAsString(value);
    }

    /** The discriminator the draft's two sealed value hierarchies need, and the only extension the derived mapper makes. */
    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.WRAPPER_OBJECT)
    interface PolymorphicValue {
    }

    /** @see PolymorphicValue */
    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.WRAPPER_OBJECT)
    interface PolymorphicBlock {
    }

    /** The draft as one document: the two components no query of this table looks into. */
    record DraftDocument(ProductDescription.Draft description, List<Media> media) {

        DraftDocument {
            media = List.copyOf(media);
        }
    }

    interface OfferDraftRows extends JpaRepository<OfferDraftRow, String> {
    }

    @Entity
    @Table(name = "offer_draft_reads")
    static class OfferDraftRow {

        @Id
        @Column(name = "offer_id")
        private String offerId;
        @Version
        private long version;
        @Enumerated(EnumType.STRING)
        private Offer.State state;
        @Column(name = "availability_start")
        private LocalDate availabilityStart;
        @Column(name = "content")
        @ColumnTransformer(read = "content::text", write = "?::jsonb")
        private String content;

        protected OfferDraftRow() {
        }

        OfferDraftRow(String offerId) {
            this.offerId = offerId;
        }

        String offerId() {
            return offerId;
        }

        Offer.State state() {
            return state;
        }

        LocalDate availabilityStart() {
            return availabilityStart;
        }

        ProductDescription.Draft description() {
            DraftDocument document = document();
            return document == null ? null : document.description();
        }

        List<Media> media() {
            DraftDocument document = document();
            return document == null ? List.of() : document.media();
        }

        OfferDraftRow copyOf(Offer offer, String document) {
            this.state = offer.state();
            this.availabilityStart = offer.availabilityStart();
            this.content = document;
            return this;
        }

        private DraftDocument document() {
            return content == null ? null : DOCUMENT.readValue(content, DraftDocument.class);
        }
    }
}
