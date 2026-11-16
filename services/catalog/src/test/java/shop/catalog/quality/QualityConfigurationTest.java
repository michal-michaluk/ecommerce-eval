package shop.catalog.quality;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import shop.catalog.IntegrationTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The wiring, not the mapping: the running container binds the {@code catalog.quality} block of the shipped
 * {@code application.yml} through {@code @ConfigurationPropertiesScan} and hands the policy the {@link QualityConfig} bean of
 * {@link QualityConfigAdapter}. {@link QualityPropertiesTest} proves the decoding; this proves that the service actually gets it.
 */
@IntegrationTest
class QualityConfigurationTest {

    @Autowired
    QualityProperties properties;
    @Autowired
    QualityConfig config;

    @Test
    void bindsTheShippedBlockIntoTheBeanThePolicyReads() {
        assertThat(properties.minProseChars()).isEqualTo(1000);
        assertThat(properties.grammar().enabled()).isFalse();
        assertThat(config.minProseChars()).isEqualTo(1000);
        assertThat(config.categories()).singleElement().satisfies(category -> {
            assertThat(category.prefix()).containsExactly("Dziecko", "Zabawki", "Klocki", "LEGO");
            assertThat(category.parameters()).hasSize(6);
        });
        assertThat(config.severityOverrides()).isEmpty();
    }
}
