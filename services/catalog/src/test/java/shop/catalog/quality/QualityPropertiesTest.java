package shop.catalog.quality;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.BindException;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.env.MockEnvironment;
import shop.catalog.offer.ProductDescription.Parameter.ValueType;
import shop.catalog.quality.QualityConfig.SeverityOverride;
import shop.catalog.quality.QualityFindings.Rule;
import shop.catalog.quality.QualityFindings.Severity;

import java.io.IOException;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The {@code catalog.quality.*} block of 08 bound through the same {@link Binder} the service uses, and the value
 * {@link QualityConfigAdapter} makes of it: the shipped {@code application.yml} decoded into the LEGO category in declaration
 * order, the two optional keys ({@code min-prose-chars}, {@code severity}) and the reserved {@code grammar.enabled}.
 */
class QualityPropertiesTest {

    @Test
    void bindsTheCategoryRulesOfD33AsAListOfPrefixAndParameters() throws IOException {
        QualityProperties properties = shipped();

        assertThat(properties.minProseChars()).isEqualTo(1000);
        assertThat(properties.requiredParameters()).singleElement().satisfies(rule -> {
            assertThat(rule.prefix()).containsExactly("Dziecko", "Zabawki", "Klocki", "LEGO");
            assertThat(rule.parameters()).containsExactly(
                    Map.entry("condition", parameter(ValueType.TEXT)),
                    Map.entry("childAge", parameter(ValueType.RANGE_FROM)),
                    Map.entry("pieceCount", parameter(ValueType.INTEGER)),
                    Map.entry("gender", parameter(ValueType.ENUM_SET)),
                    Map.entry("certificates", parameter(ValueType.LIST)),
                    Map.entry("safetyInfo", parameter(ValueType.TEXT)));
        });
    }

    @Test
    void mapsTheShippedPropertiesToTheValueThePolicyTakes() throws IOException {
        QualityConfig config = adapter().qualityConfig(shipped());

        assertThat(config.minProseChars()).isEqualTo(1000);
        assertThat(config.categories()).singleElement().satisfies(category -> {
            assertThat(category.prefix()).containsExactly("Dziecko", "Zabawki", "Klocki", "LEGO");
            assertThat(category.parameters()).containsExactly(
                    QualityConfigFixture.requiredParameter("condition", ValueType.TEXT),
                    QualityConfigFixture.requiredParameter("childAge", ValueType.RANGE_FROM),
                    QualityConfigFixture.requiredParameter("pieceCount", ValueType.INTEGER),
                    QualityConfigFixture.requiredParameter("gender", ValueType.ENUM_SET),
                    QualityConfigFixture.requiredParameter("certificates", ValueType.LIST),
                    QualityConfigFixture.requiredParameter("safetyInfo", ValueType.TEXT));
        });
        assertThat(config.severityOverrides()).isEmpty();
    }

    @Test
    void bindsTheStrictMediaOverrideOfTheVariantTable() {
        QualityProperties properties = bound("catalog.quality.severity.QP-8", "blocking");

        assertThat(properties.minProseChars()).isEqualTo(1000);
        assertThat(adapter().qualityConfig(properties).severityOverrides())
                .containsExactly(new SeverityOverride(Rule.QP_8, Severity.BLOCKING));
    }

    @Test
    void refusesASeverityOverrideForAnUnknownRule() {
        assertThatThrownBy(() -> bound("catalog.quality.severity.QP-9", "blocking"))
                .isInstanceOf(BindException.class)
                .hasMessageContaining("catalog.quality.severity")
                .rootCause()
                .hasMessageContaining("QP-9");
    }

    @Test
    void yieldsTheUniversalRulesOnlyWhenNoCategoryIsConfigured() {
        QualityConfig config = adapter().qualityConfig(bound("catalog.quality.min-prose-chars", "1000"));

        assertThat(config).isEqualTo(QualityConfig.defaultConfig());
    }

    @Test
    void bindsGrammarEnabledAndTheValueHasNoGrammar() {
        QualityProperties properties = bound("catalog.quality.grammar.enabled", "true");

        assertThat(properties.grammar().enabled()).isTrue();
        assertThat(adapter().qualityConfig(properties)).isEqualTo(QualityConfig.defaultConfig());
    }

    private static QualityProperties.Parameter parameter(ValueType valueType) {
        return new QualityProperties.Parameter(valueType, true);
    }

    private static QualityConfigAdapter adapter() {
        return new QualityConfigAdapter();
    }

    /** The block the service actually ships, decoded from {@code application.yml} through the binder's own YAML loader. */
    private static QualityProperties shipped() throws IOException {
        MockEnvironment environment = new MockEnvironment();
        for (PropertySource<?> source : new YamlPropertySourceLoader()
                .load("application", new ClassPathResource("application.yml"))) {
            environment.getPropertySources().addLast(source);
        }
        return bind(environment);
    }

    private static QualityProperties bound(String... keyValuePairs) {
        MockEnvironment environment = new MockEnvironment();
        for (int index = 0; index < keyValuePairs.length; index += 2) {
            environment.setProperty(keyValuePairs[index], keyValuePairs[index + 1]);
        }
        return bind(environment);
    }

    private static QualityProperties bind(MockEnvironment environment) {
        return Binder.get(environment)
                .bind("catalog.quality", Bindable.of(QualityProperties.class))
                .orElseThrow(() -> new IllegalStateException("catalog.quality did not bind"));
    }
}
