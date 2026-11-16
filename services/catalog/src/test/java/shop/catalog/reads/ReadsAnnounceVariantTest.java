package shop.catalog.reads;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import shop.catalog.IntegrationTest;
import shop.catalog.offer.Offer;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The {@code announce} variant of 11 as configuration: {@code catalog.visibility.future = announce} turns R2 from hidden into
 * {@code VISIBLE_SCHEDULED}, and {@code catalog.timezone} decides which date "today" is.
 */
@IntegrationTest
@TestPropertySource(properties = {"catalog.visibility.future=announce", "catalog.timezone=UTC"})
class ReadsAnnounceVariantTest {

    @Autowired
    OfferVisibility visibility;

    @Test
    void announcesAPublishedOfferThatIsNotAvailableYet() {
        assertThat(visibility.visibilityOf(Offer.State.PUBLISHED, LocalDate.now().plusDays(30)))
                .isEqualTo(OfferVisibility.Outcome.VISIBLE_SCHEDULED);
    }

    @Test
    void readsTheConfiguredTimezone() {
        assertThat(visibility.timezone()).hasToString("UTC");
        assertThat(visibility.future()).isEqualTo(OfferVisibility.Future.ANNOUNCE);
    }
}
