package shop.catalog.review;

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
import shop.catalog.offer.ProductDescription;
import shop.catalog.tools.JsonConfiguration;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The {@link ReviewRepository} adapter, document plus history: one row per review holding the frozen candidate and the decision, one
 * row per finding the candidate carries, and one row per emitted domain event.
 *
 * <p>The candidate is one JSONB document because nothing queries into it — every read loads it whole, which is what "frozen by
 * value" means for the review (RULE-3, D38). The columns outside it are the ones a query filters on ({@code offer_id}) and the facts
 * the read side lists without loading a candidate: the state, who asked, who decided, the reason and the published version.
 *
 * <p>The findings are rows, not part of the document, because the reviewer screen and an audit both read them as a list — the
 * reported report and the override's ignored findings, told apart by {@link Source} so a rehydration rebuilds the
 * {@link ReviewOverride} without inferring it from a missing override actor.
 *
 * <p>The adapter drains {@link ReviewProcess#events} before writing, then publishes the {@link Review} snapshot once for a changed
 * save and each {@link ReviewEvent} once, inside the caller's transaction, after the rows are written (D34).
 *
 * <p>The JSON is written with a mapper derived from the one process-wide mapper, because the frozen description embeds two sealed
 * value hierarchies ({@code Parameter.Value}, {@code DescriptionBlock}) that carry no {@code @JsonTypeInfo} of their own — a domain
 * type does not name its own wire format. The derived mapper adds that discriminator and nothing else, and it is declared here
 * because only this adapter stores that value.
 */
@Primary
@Repository
class DocumentReviewRepository implements ReviewRepository {

    private static final ObjectMapper DOCUMENT = JsonConfiguration.OBJECT_MAPPER.rebuild()
            .addMixIn(ProductDescription.Parameter.Value.class, PolymorphicValue.class)
            .addMixIn(ProductDescription.DescriptionBlock.class, PolymorphicBlock.class)
            .build();

    private final ReviewDocuments documents;
    private final ReviewFindings findings;
    private final ReviewHistory history;
    private final ApplicationEventPublisher publisher;
    private final Clock clock;

    DocumentReviewRepository(ReviewDocuments documents, ReviewFindings findings, ReviewHistory history,
                             ApplicationEventPublisher publisher, Clock clock) {
        this.documents = documents;
        this.findings = findings;
        this.history = history;
        this.publisher = publisher;
        this.clock = clock;
    }

    @Override
    public Optional<ReviewProcess> get(String reviewId) {
        return documents.findById(reviewId).map(this::rehydrate);
    }

    @Override
    public List<Review> reviewsOf(String offerId) {
        return documents.findByOfferIdOrderByRequestedAtAsc(offerId).stream()
                .map(this::rehydrate)
                .map(ReviewProcess::toReview)
                .toList();
    }

    @Override
    public void save(ReviewProcess review) {
        List<ReviewEvent> emitted = eventsEmittedFrom(review);
        Review snapshot = review.toReview();
        documents.save(documentOf(snapshot));
        findings.deleteAll(findings.findByReviewIdOrderBySourceAscFindingPositionAsc(snapshot.reviewId()));
        findings.saveAll(findingRowsOf(snapshot));
        emitted.forEach(event -> history.save(new ReviewHistoryEntity(snapshot.reviewId(), Instant.now(clock), event)));
        if (!emitted.isEmpty()) {
            publisher.publishEvent(snapshot);
        }
        emitted.forEach(event -> publisher.publishEvent(event));
    }

    /** Copies the aggregate's buffer and clears it before the write, so a failed save cannot publish a fact twice. */
    static List<ReviewEvent> eventsEmittedFrom(ReviewProcess review) {
        List<ReviewEvent> emitted = List.copyOf(review.events);
        review.events.clear();
        return emitted;
    }

    private ReviewProcess rehydrate(ReviewProcessEntity entity) {
        CandidateDocument candidate = read(entity.candidate(), CandidateDocument.class);
        List<ReviewFindingEntity> rows = findings.findByReviewIdOrderBySourceAscFindingPositionAsc(entity.reviewId());
        ReviewCandidate frozen = new ReviewCandidate(candidate.description(), candidate.media(), entity.availabilityStart(),
                findingsOf(rows, Source.REPORTED), overrideOf(entity, rows));
        return new ReviewProcess(entity.reviewId(), new ArrayList<>(), entity.offerId(), entity.requestedBy(),
                entity.requestedAt(), frozen, entity.state(), entity.reviewedBy(), entity.reviewedAt(), entity.reason(),
                entity.publishedOfferVersion());
    }

    private ReviewProcessEntity documentOf(Review snapshot) {
        ReviewProcessEntity document = documents.findById(snapshot.reviewId())
                .orElseGet(() -> new ReviewProcessEntity(snapshot.reviewId()));
        ReviewCandidate candidate = snapshot.candidate();
        CandidateDocument frozen = new CandidateDocument(candidate.description(), candidate.media());
        return document.setReview(snapshot, write(frozen));
    }

    private static List<ReviewFindingEntity> findingRowsOf(Review snapshot) {
        List<ReviewFindingEntity> rows = new ArrayList<>(
                findingRows(snapshot.reviewId(), Source.REPORTED, snapshot.candidate().findings()));
        ReviewOverride override = snapshot.candidate().override();
        if (override != null) {
            rows.addAll(findingRows(snapshot.reviewId(), Source.IGNORED, override.ignoredFindings()));
        }
        return rows;
    }

    private static List<ReviewFindingEntity> findingRows(String reviewId, Source source, List<ReviewFinding> findings) {
        List<ReviewFindingEntity> rows = new ArrayList<>();
        for (int position = 0; position < findings.size(); position++) {
            rows.add(new ReviewFindingEntity(reviewId, source, position, findings.get(position)));
        }
        return rows;
    }

    private static List<ReviewFinding> findingsOf(List<ReviewFindingEntity> rows, Source source) {
        return rows.stream()
                .filter(row -> row.source().equals(source))
                .map(ReviewFindingEntity::toFinding)
                .toList();
    }

    private static ReviewOverride overrideOf(ReviewProcessEntity entity, List<ReviewFindingEntity> rows) {
        if (entity.overrideActor() == null) {
            return null;
        }
        return new ReviewOverride(entity.overrideActor(), entity.overrideRecordedAt(), findingsOf(rows, Source.IGNORED));
    }

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

    /** The frozen candidate as one document: the two components the review never queries into, stored and read as a whole. */
    record CandidateDocument(ProductDescription description, List<ReviewMedia> media) {

        CandidateDocument {
            media = List.copyOf(media);
        }
    }

    /** Which list of findings a stored row belongs to: the report 08 gave, or the findings an override takes responsibility for. */
    enum Source {
        REPORTED,
        IGNORED
    }

    interface ReviewDocuments extends JpaRepository<ReviewProcessEntity, String> {

        List<ReviewProcessEntity> findByOfferIdOrderByRequestedAtAsc(String offerId);
    }

    interface ReviewFindings extends JpaRepository<ReviewFindingEntity, Long> {

        List<ReviewFindingEntity> findByReviewIdOrderBySourceAscFindingPositionAsc(String reviewId);
    }

    interface ReviewHistory extends JpaRepository<ReviewHistoryEntity, Long> {

        List<ReviewHistoryEntity> findByReviewIdOrderById(String reviewId);
    }

    @Entity
    @Table(name = "review_process")
    static class ReviewProcessEntity {

        @Id
        @Column(name = "review_id")
        private String reviewId;
        @Version
        private long version;
        @Column(name = "offer_id")
        private String offerId;
        @Enumerated(EnumType.STRING)
        private Review.State state;
        @Column(name = "requested_by")
        private String requestedBy;
        @Column(name = "requested_at")
        private Instant requestedAt;
        @Column(name = "reviewed_by")
        private String reviewedBy;
        @Column(name = "reviewed_at")
        private Instant reviewedAt;
        private String reason;
        @Column(name = "published_offer_version")
        private Integer publishedOfferVersion;
        @Column(name = "availability_start")
        private LocalDate availabilityStart;
        @Column(name = "override_actor")
        private String overrideActor;
        @Column(name = "override_recorded_at")
        private Instant overrideRecordedAt;
        @Column(name = "candidate")
        @ColumnTransformer(read = "candidate::text", write = "?::jsonb")
        private String candidate;

        protected ReviewProcessEntity() {
        }

        ReviewProcessEntity(String reviewId) {
            this.reviewId = reviewId;
        }

        String reviewId() {
            return reviewId;
        }

        String offerId() {
            return offerId;
        }

        Review.State state() {
            return state;
        }

        String requestedBy() {
            return requestedBy;
        }

        Instant requestedAt() {
            return requestedAt;
        }

        String reviewedBy() {
            return reviewedBy;
        }

        Instant reviewedAt() {
            return reviewedAt;
        }

        String reason() {
            return reason;
        }

        Integer publishedOfferVersion() {
            return publishedOfferVersion;
        }

        LocalDate availabilityStart() {
            return availabilityStart;
        }

        String overrideActor() {
            return overrideActor;
        }

        Instant overrideRecordedAt() {
            return overrideRecordedAt;
        }

        String candidate() {
            return candidate;
        }

        ReviewProcessEntity setReview(Review review, String frozenCandidate) {
            ReviewOverride override = review.candidate().override();
            this.offerId = review.offerId();
            this.state = review.state();
            this.requestedBy = review.requestedBy();
            this.requestedAt = review.requestedAt();
            this.reviewedBy = review.reviewedBy();
            this.reviewedAt = review.reviewedAt();
            this.reason = review.reason();
            this.publishedOfferVersion = review.publishedOfferVersion();
            this.availabilityStart = review.candidate().availabilityStart();
            this.overrideActor = override == null ? null : override.actor();
            this.overrideRecordedAt = override == null ? null : override.recordedAt();
            this.candidate = frozenCandidate;
            return this;
        }
    }

    @Entity
    @Table(name = "review_finding")
    static class ReviewFindingEntity {

        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;
        @Column(name = "review_id")
        private String reviewId;
        @Enumerated(EnumType.STRING)
        private Source source;
        @Column(name = "finding_position")
        private int findingPosition;
        private String rule;
        private String field;
        @Enumerated(EnumType.STRING)
        private ReviewFinding.Severity severity;
        private String message;

        protected ReviewFindingEntity() {
        }

        ReviewFindingEntity(String reviewId, Source source, int findingPosition, ReviewFinding finding) {
            this.reviewId = reviewId;
            this.source = source;
            this.findingPosition = findingPosition;
            this.rule = finding.rule();
            this.field = finding.field();
            this.severity = finding.severity();
            this.message = finding.message();
        }

        Source source() {
            return source;
        }

        ReviewFinding toFinding() {
            return new ReviewFinding(rule, field, severity, message);
        }
    }

    @Entity
    @Table(name = "review_history")
    static class ReviewHistoryEntity {

        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;
        @Column(name = "review_id")
        private String reviewId;
        @Column(name = "recorded_at")
        private Instant recordedAt;
        @Column(name = "event_type")
        private String eventType;
        @Column(name = "payload")
        @ColumnTransformer(read = "payload::text", write = "?::jsonb")
        private String payload;

        protected ReviewHistoryEntity() {
        }

        ReviewHistoryEntity(String reviewId, Instant recordedAt, ReviewEvent event) {
            this.reviewId = reviewId;
            this.recordedAt = recordedAt;
            this.eventType = event.getClass().getSimpleName();
            this.payload = write(event);
        }

        String eventType() {
            return eventType;
        }
    }
}
