package shop.catalog.reads;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Binds the two keys 11 reads — {@code catalog.timezone} and {@code catalog.visibility.future} — into the values
 * {@link OfferVisibility} takes, so the policy stays framework-free and every default is declared once, in code, where a service
 * that configures neither still answers the accepted variant (17 {@code reads → tools}).
 *
 * <p>The prefix is {@code catalog}, not a {@code catalog.reads}, because both keys are 11's own and already named by the spec; a
 * third key would be a third component of {@link ReadsSettings}, never a new prefix.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ReadsConfiguration.ReadsSettings.class)
class ReadsConfiguration {

    @Bean
    OfferVisibility offerVisibility(ReadsSettings settings, Clock clock) {
        return OfferVisibility.of(clock, settings.timezone(), settings.visibility().future());
    }

    /** 11's configuration as a value: the business timezone and which variant R2 answers with. */
    @ConfigurationProperties(prefix = "catalog")
    record ReadsSettings(String timezone, VisibilitySettings visibility) {

        static final String BUSINESS_TIMEZONE = "Europe/Warsaw";

        ReadsSettings {
            timezone = timezone == null || timezone.isBlank() ? BUSINESS_TIMEZONE : timezone;
            visibility = visibility == null ? new VisibilitySettings(null) : visibility;
        }
    }

    /** {@code catalog.visibility.*}: the default variant hides a published offer until it is available (Q10). */
    record VisibilitySettings(OfferVisibility.Future future) {

        VisibilitySettings {
            future = future == null ? OfferVisibility.Future.HIDDEN : future;
        }
    }
}
