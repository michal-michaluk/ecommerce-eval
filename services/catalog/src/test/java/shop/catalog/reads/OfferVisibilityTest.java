package shop.catalog.reads;

import org.junit.jupiter.api.Test;
import shop.catalog.offer.Offer;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static shop.catalog.reads.OfferVisibility.Outcome.HIDDEN;
import static shop.catalog.reads.OfferVisibility.Outcome.VISIBLE;
import static shop.catalog.reads.OfferVisibility.Outcome.VISIBLE_SCHEDULED;
import static shop.catalog.reads.ReadsFixture.TODAY;
import static shop.catalog.reads.ReadsFixture.announceOn;
import static shop.catalog.reads.ReadsFixture.visibilityOn;

/**
 * Element 11's executable scenarios S1–S8, the variant {@code catalog.visibility.future}, the business date of
 * {@code catalog.timezone}, and the D37 case: a published offer whose candidate is under review is still visible, because an open
 * review never moves a live offer to {@code pendingReview}.
 */
class OfferVisibilityTest {

    @Test
    void s1ShowsAPublishedOfferWithoutAnAvailabilityDate() {
        assertThat(visibilityOn(TODAY).visibilityOf(Offer.State.PUBLISHED, null, TODAY)).isEqualTo(VISIBLE);
    }

    @Test
    void s2ShowsAPublishedOfferWhoseAvailabilityHasStarted() {
        assertThat(visibilityOn(TODAY).visibilityOf(Offer.State.PUBLISHED, LocalDate.of(2026, 5, 10), TODAY))
                .isEqualTo(VISIBLE);
    }

    @Test
    void s3HidesAPublishedOfferThatIsNotAvailableYet() {
        assertThat(visibilityOn(TODAY).visibilityOf(Offer.State.PUBLISHED, LocalDate.of(2026, 7, 1), TODAY))
                .isEqualTo(HIDDEN);
    }

    @Test
    void s4ShowsAPublishedOfferOnItsOwnAvailabilityDate() {
        assertThat(visibilityOn(TODAY).visibilityOf(Offer.State.PUBLISHED, TODAY, TODAY)).isEqualTo(VISIBLE);
    }

    @Test
    void s5AnnouncesAPublishedOfferThatIsNotAvailableYetUnderTheAnnounceVariant() {
        assertThat(announceOn(TODAY).visibilityOf(Offer.State.PUBLISHED, LocalDate.of(2026, 7, 1), TODAY))
                .isEqualTo(VISIBLE_SCHEDULED);
    }

    @Test
    void s6HidesAnOfferThatWasNeverPublished() {
        assertThat(visibilityOn(TODAY).visibilityOf(Offer.State.PENDING_REVIEW, null, TODAY)).isEqualTo(HIDDEN);
    }

    @Test
    void s7HidesADraft() {
        assertThat(visibilityOn(TODAY).visibilityOf(Offer.State.DRAFT, null, TODAY)).isEqualTo(HIDDEN);
    }

    @Test
    void s8HidesARemovedOfferWhateverItsAvailability() {
        assertThat(visibilityOn(TODAY).visibilityOf(Offer.State.REMOVED, LocalDate.of(2026, 5, 10), TODAY))
                .isEqualTo(HIDDEN);
    }

    /** R2 becomes R1 at local midnight of the availability date, read in the business timezone and never by the wall clock. */
    @Test
    void rollsOverAtMidnightInTheBusinessTimezone() {
        LocalDate availabilityStart = LocalDate.of(2026, 6, 16);

        assertThat(visibilityOn(LocalDate.of(2026, 6, 15)).visibilityOf(Offer.State.PUBLISHED, availabilityStart))
                .isEqualTo(HIDDEN);
        assertThat(visibilityAt("2026-06-15T21:30:00Z").visibilityOf(Offer.State.PUBLISHED, availabilityStart))
                .isEqualTo(HIDDEN);
        assertThat(visibilityAt("2026-06-15T22:30:00Z").visibilityOf(Offer.State.PUBLISHED, availabilityStart))
                .isEqualTo(VISIBLE);
    }

    private static OfferVisibility visibilityAt(String instant) {
        return OfferVisibility.of(Clock.fixed(Instant.parse(instant), ZoneOffset.UTC),
                ReadsFixture.BUSINESS_ZONE.getId(), OfferVisibility.Future.HIDDEN);
    }

    /** The announce variant only ever changes R2: every other rule of 11 answers exactly as the default variant does. */
    @Test
    void announceNeverChangesARuleOtherThanR2() {
        OfferVisibility announce = announceOn(TODAY);

        assertThat(announce.visibilityOf(Offer.State.PUBLISHED, LocalDate.of(2026, 6, 1), TODAY)).isEqualTo(VISIBLE);
        assertThat(announce.visibilityOf(Offer.State.DRAFT, LocalDate.of(2026, 6, 1), TODAY)).isEqualTo(HIDDEN);
        assertThat(announce.visibilityOf(Offer.State.PENDING_REVIEW, null, TODAY)).isEqualTo(HIDDEN);
        assertThat(announce.visibilityOf(Offer.State.REMOVED, null, TODAY)).isEqualTo(HIDDEN);
    }
}
