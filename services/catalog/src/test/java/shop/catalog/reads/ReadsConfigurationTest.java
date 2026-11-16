package shop.catalog.reads;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import shop.catalog.IntegrationTest;
import shop.catalog.offer.Offer;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 11's configuration keys as the service binds them when nobody configures them: {@code catalog.timezone} defaults to the business
 * timezone and {@code catalog.visibility.future} defaults to {@code hidden}, the variant that leaks the least (Q10).
 */
@IntegrationTest
class ReadsConfigurationTest {

    @Autowired
    OfferVisibility visibility;

    @Test
    void hidesAPublishedOfferThatIsNotAvailableYetByDefault() {
        assertThat(visibility.visibilityOf(Offer.State.PUBLISHED, LocalDate.now().plusDays(30)))
                .isEqualTo(OfferVisibility.Outcome.HIDDEN);
    }

    @Test
    void answersTheBusinessTimezoneInThePolicy() {
        assertThat(visibility.timezone()).hasToString(ReadsConfiguration.ReadsSettings.BUSINESS_TIMEZONE);
        assertThat(visibility.future()).isEqualTo(OfferVisibility.Future.HIDDEN);
    }
}
