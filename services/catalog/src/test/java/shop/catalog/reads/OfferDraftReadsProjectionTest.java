package shop.catalog.reads;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import shop.catalog.IntegrationTest;
import shop.catalog.offer.Offer;
import shop.catalog.offer.ProductDescriptionDraftFixture;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static shop.catalog.reads.QualityFinding.Severity.BLOCKING;
import static shop.catalog.reads.ReadsFixture.OFFER_ID;

/**
 * The draft read model of 14 against a real database: the handler copies the offer snapshot into its own row, a re-delivered
 * snapshot rewrites the same row instead of adding one, and any two snapshots of one offer end in the row of the one applied last.
 *
 * <p>The test calls the handler directly, so a broken publisher and a broken projection fail in different tests
 * ({@code test.projection-handlers-direct}).
 */
@IntegrationTest
class OfferDraftReadsProjectionTest {

    @Autowired
    OfferDraftReadsProjection projection;
    @Autowired
    OfferDraftReadsProjection.OfferDraftRows rows;

    @BeforeEach
    void clearsTheReadModel() {
        rows.deleteAll();
    }

    @Test
    void keepsOneRowForTheSameSnapshotDeliveredTwice() {
        Offer offer = ReadsFixture.draftOffer(OFFER_ID);

        projection.handle(offer);
        projection.handle(offer);

        assertThat(rows.count()).isEqualTo(1);
        OfferDraftReads reads = projection.findDraft(OFFER_ID).orElseThrow();
        assertThat(reads.state()).isEqualTo(Offer.State.DRAFT);
        assertThat(reads.description().title()).isEqualTo(offer.description().title());
        assertThat(reads.media()).isEqualTo(offer.media());
    }

    /** Either arrival order of two snapshots ends in the row of the later one, because every handler recomputes the whole row. */
    @Test
    void endsInTheSameRowWhicheverSnapshotArrivedFirst() {
        Offer earlier = ReadsFixture.draftOffer(OFFER_ID);
        Offer later = ReadsFixture.offer(OFFER_ID, Offer.State.PUBLISHED, LocalDate.of(2026, 5, 10),
                ProductDescriptionDraftFixture.draftWithTitle("LEGO Star Wars 75423 (po korekcie autora)"));

        projection.handle(later);
        OfferDraftReads afterLaterOnly = projection.findDraft(OFFER_ID).orElseThrow();

        rows.deleteAll();
        projection.handle(earlier);
        projection.handle(later);
        OfferDraftReads afterEarlierThenLater = projection.findDraft(OFFER_ID).orElseThrow();

        assertThat(afterEarlierThenLater).isEqualTo(afterLaterOnly);
        assertThat(afterEarlierThenLater.state()).isEqualTo(Offer.State.PUBLISHED);
    }

    /** 14's panel: the findings are 08's answer about the stored draft, asked when the screen is served. */
    @Test
    void reportsTheFindingsOfTheDraftItStores() {
        projection.handle(ReadsFixture.offer(OFFER_ID, Offer.State.DRAFT, null, ProductDescriptionDraftFixture.blankDraft(),
                List.of()));

        OfferDraftReads reads = projection.findDraft(OFFER_ID).orElseThrow();
        List<QualityFinding> findings = reads.findings();

        assertThat(findings).extracting(QualityFinding::rule).containsExactly("QP-4", "QP-5", "QP-6", "QP-6");
        assertThat(findings).extracting(QualityFinding::severity).containsOnly(BLOCKING);
        assertThat(reads.blockingFindings()).hasSize(4);
    }

    /** The same draft with a publishable media list reports the prose findings only: the panel follows the stored content. */
    @Test
    void reportsOnlyWhatTheStoredContentStillViolates() {
        projection.handle(ReadsFixture.offer(OFFER_ID, Offer.State.DRAFT, null, ProductDescriptionDraftFixture.blankDraft()));

        assertThat(projection.findDraft(OFFER_ID).orElseThrow().findings())
                .extracting(QualityFinding::rule).containsExactly("QP-6", "QP-6");
    }

    /** 11 R4/R5 on the read side: a never-published and a removed offer are hidden at any date. */
    @Test
    void hidesTheDraftAndTheRemovedOffer() {
        projection.handle(ReadsFixture.draftOffer(OFFER_ID));
        assertThat(projection.findDraft(OFFER_ID).orElseThrow().visibility()).isEqualTo(OfferVisibility.Outcome.HIDDEN);

        projection.handle(ReadsFixture.removedOffer(OFFER_ID));
        assertThat(projection.findDraft(OFFER_ID).orElseThrow().visibility()).isEqualTo(OfferVisibility.Outcome.HIDDEN);
    }

    @Test
    void answersEmptyForAnOfferThatWasNeverWritten() {
        assertThat(projection.findDraft("PRD-UNKNOWN")).isEmpty();
    }
}
