package shop.catalog.review;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.Objects;

/**
 * Binds {@code catalog.publish.override.enabled} into the value {@link ReviewProcess} asks for when a review is requested, so the
 * model takes the switch as a value instead of reading configuration (D27, D29, 15's {@code no-override} variant).
 *
 * <p>It is one fact and one place: the override exists only at the request, so nothing else in the service needs the key.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ReviewConfiguration.OverrideSettings.class)
class ReviewConfiguration {

    /** The switch of 04: with the override disabled a request may not carry findings at all, and a reviewer never can (D27). */
    @ConfigurationProperties(prefix = "catalog.publish.override")
    record OverrideSettings(Boolean enabled) {

        static final boolean DEFAULT_ENABLED = true;

        OverrideSettings {
            enabled = Objects.requireNonNullElse(enabled, DEFAULT_ENABLED);
        }

        boolean allowsOverride() {
            return enabled;
        }
    }
}
