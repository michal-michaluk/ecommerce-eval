package shop.catalog.reads;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import shop.catalog.review.Review;

import java.time.Clock;
import java.time.Instant;

/**
 * The pending-reviews list of 15, built from the review snapshot and answered as a page of rows.
 *
 * <p>Its edge is the {@link Review} snapshot, which already carries everything the list renders — the frozen candidate's title, the
 * requester, the request instant and the findings — so the projection copies those and asks the review context nothing (17
 * RULE-1, {@code integration.payload-complete}). Rows are upserted by {@code reviewId}, so a re-publication rewrites the same row and
 * every decided review keeps the state that decides whether it is listed.
 *
 * <p>{@code waitingFor} is the one derived component, computed when the list is served instead of stored: it is a function of the
 * clock, and a stored value would be as old as the last write to the row (15 RULE-1).
 *
 * <p>The {@link Review.State} column and the repository filter that reads it are not a producer type in an API:
 * {@code http.no-persistence-type-in-api} bars an entity, a snapshot or a repository, while this is the review lifecycle 04 exports
 * and 15 RULE-6 drops rows by — and a local copy of it would be a second vocabulary for one closed set
 * ({@code contract.no-local-copy}). The query itself answers {@link PendingReviewsReads}, this context's own record.
 */
@Component
@Transactional
@AllArgsConstructor
class PendingReviewsProjection {

    private final PendingReviews rows;
    private final Clock clock;

    @EventListener
    public void handle(Review review) {
        PendingReviewRow row = rows.findById(review.reviewId())
                .orElseGet(() -> new PendingReviewRow(review.reviewId()));
        rows.save(row.copyOf(review));
    }

    /**
     * The reviews still waiting for a decision, oldest first, without the caller's own requests (04 RULE-2, 15 RULE-4): the filter
     * lives in the query so a caller cannot ask for a list they must not see and then forget to filter it.
     */
    @Transactional(readOnly = true)
    public Page<PendingReviewsReads> findAllPending(String reviewer, Pageable pageable) {
        Instant now = Instant.now(clock);
        return rows.findAllByStateAndRequestedByNotOrderByRequestedAtAsc(
                        Review.State.REQUESTED, reviewer, pageable)
                .map(row -> PendingReviewsReads.ofNullable(row, now));
    }

    interface PendingReviews extends JpaRepository<PendingReviewRow, String> {

        Page<PendingReviewRow> findAllByStateAndRequestedByNotOrderByRequestedAtAsc(
                Review.State state, String requestedBy, Pageable pageable);
    }

    @Entity
    @Table(name = "pending_reviews_reads")
    static class PendingReviewRow {

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
        private String title;
        @Column(name = "findings_count")
        private int findingsCount;

        protected PendingReviewRow() {
        }

        PendingReviewRow(String reviewId) {
            this.reviewId = reviewId;
        }

        String reviewId() {
            return reviewId;
        }

        String offerId() {
            return offerId;
        }

        String title() {
            return title;
        }

        String requestedBy() {
            return requestedBy;
        }

        Instant requestedAt() {
            return requestedAt;
        }

        int findingsCount() {
            return findingsCount;
        }

        PendingReviewRow copyOf(Review review) {
            this.offerId = review.offerId();
            this.state = review.state();
            this.requestedBy = review.requestedBy();
            this.requestedAt = review.requestedAt();
            this.title = review.candidate().description().title();
            this.findingsCount = review.candidate().findings().size();
            return this;
        }
    }
}
