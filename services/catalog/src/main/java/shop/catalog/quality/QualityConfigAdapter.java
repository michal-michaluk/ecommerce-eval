package shop.catalog.quality;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import shop.catalog.quality.QualityConfig.CategoryParameters;
import shop.catalog.quality.QualityConfig.RequiredParameter;
import shop.catalog.quality.QualityConfig.SeverityOverride;

/**
 * The only place {@code catalog.quality.*} becomes the value {@link QualityPolicy} takes: the domain sees no Spring type and the
 * binder sees no domain value.
 *
 * <p>Nothing but names is translated. The list order of {@code required-parameters} and of each {@code parameters} block arrives
 * as declared, because that order is the report order of QP-10…QP-12 (D33), and {@code grammar.enabled} has no counterpart in the
 * value: QP-9 is out of scope (D29). A configuration that names no category produces the shipped prose threshold and no category
 * set — 08's "no match" variant, which is the same value as {@link QualityConfig#defaultConfig()} and not a fallback invented
 * here: the LEGO category 08 ships lives in {@code application.yml}, because a category set is configuration, not behaviour.
 *
 * <p>The policy itself is a bean here too, because this is the only place that knows the configuration the policy reads: the
 * mediator of the {@code review → quality} edge injects {@link QualityPolicy} and never the {@link QualityConfig} behind it.
 */
@Configuration(proxyBeanMethods = false)
class QualityConfigAdapter {

    @Bean
    QualityConfig qualityConfig(QualityProperties properties) {
        return new QualityConfig(
                properties.minProseChars(),
                properties.requiredParameters().stream().map(QualityConfigAdapter::category).toList(),
                properties.severity().entrySet().stream()
                        .map(configured -> new SeverityOverride(configured.getKey(), configured.getValue()))
                        .toList());
    }

    @Bean
    QualityPolicy qualityPolicy(QualityConfig config) {
        return QualityPolicy.withConfig(config);
    }

    private static CategoryParameters category(QualityProperties.CategoryRule rule) {
        return new CategoryParameters(rule.prefix(), rule.parameters().entrySet().stream()
                .map(configured -> new RequiredParameter(
                        configured.getKey(), configured.getValue().valueType(), configured.getValue().required()))
                .toList());
    }
}
