package shop.catalog.quality;

import shop.catalog.offer.ProductDescription.Parameter.ValueType;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The bound configuration of 08 (key {@code catalog.quality}) as a value: the prose threshold, the required parameters per
 * category prefix and the per-rule severity overrides. A caller hands an instance to {@link QualityPolicy#withConfig}, so the
 * policy reads no configuration utility and several configurations can coexist (one per category set, one per test).
 *
 * <p>{@code minProseChars} defaults to {@value #DEFAULT_MIN_PROSE_CHARS}: the measured prose of the LEGO sample is 1 626, and
 * 1 000 is that sample rounded down to a round number at ~61 % of it — above the longest single paragraph (449), so no single
 * block can satisfy the rule.
 *
 * <p>The required parameters are a list of {@code {prefix, parameters}}, never a map keyed by the prefix: a category path is a
 * list and a map key cannot carry one. Each category's parameter order is the report order of QP-10…QP-12 (D33).
 *
 * <p>It stays internal although {@link QualityPolicy} is public: the mediator of the {@code review → quality} edge injects the
 * policy bean and names no configuration, so a public type here would be an export no consumer reads (D42 is read as "the types
 * the edge names", not as a list to widen).
 */
record QualityConfig(
        int minProseChars,
        List<CategoryParameters> categories,
        List<SeverityOverride> severityOverrides) {

    static final int DEFAULT_MIN_PROSE_CHARS = 1000;

    QualityConfig {
        if (minProseChars < 1) {
            throw new QualityException(QualityException.Error.MIN_PROSE_CHARS_NOT_POSITIVE, minProseChars);
        }
        categories = List.copyOf(categories);
        severityOverrides = List.copyOf(severityOverrides);
        ensureSeveritiesAreDeclaredOnce(severityOverrides);
    }

    /**
     * The fallback of the value, for a service that configures no category: the shipped prose threshold and the universal rules
     * only. The shipped category set is not a code default — 08 gets it from {@code catalog.quality.required-parameters}, because
     * "one category (LEGO) is configured today" is configuration, not behaviour.
     */
    static QualityConfig defaultConfig() {
        return new QualityConfig(DEFAULT_MIN_PROSE_CHARS, List.of(), List.of());
    }

    /** The severity in force for {@code rule}: the override named for it, otherwise the one 08's table gives. */
    QualityFindings.Severity severityOf(QualityFindings.Rule rule) {
        return severityOverrides.stream()
                .filter(override -> override.rule() == rule)
                .map(SeverityOverride::severity)
                .findFirst()
                .orElseGet(rule::severity);
    }

    private static void ensureSeveritiesAreDeclaredOnce(List<SeverityOverride> overrides) {
        long declaredRules = overrides.stream().map(SeverityOverride::rule).distinct().count();
        if (declaredRules != overrides.size()) {
            throw new QualityException(QualityException.Error.SEVERITY_DECLARED_TWICE, overrides);
        }
    }

    /** The rule set of one category prefix, its parameters in declaration order. */
    record CategoryParameters(List<String> prefix, List<RequiredParameter> parameters) {

        CategoryParameters {
            if (prefix == null || prefix.isEmpty()) {
                throw new QualityException(QualityException.Error.CATEGORY_PREFIX_MISSING);
            }
            prefix = List.copyOf(prefix);
            parameters = List.copyOf(parameters);
        }

        /** The match of 08: the draft's path starts with this prefix, so a deeper category keeps its ancestor's rules. */
        boolean matches(List<String> categoryPath) {
            return categoryPath.size() >= prefix.size()
                   && categoryPath.subList(0, prefix.size()).equals(prefix);
        }

        Optional<RequiredParameter> parameter(String code) {
            return parameters.stream().filter(parameter -> parameter.code().equals(code)).findFirst();
        }

        /** The name the QP-10…QP-12 messages carry: the last category of the prefix, so a reviewer sees {@code LEGO}. */
        String name() {
            return prefix.get(prefix.size() - 1);
        }
    }

    /** One parameter a category configures; {@code required} is false for a parameter the category allows to be absent. */
    record RequiredParameter(String code, ValueType valueType, boolean required) {

        RequiredParameter {
            if (code == null || code.isBlank()) {
                throw new QualityException(QualityException.Error.PARAMETER_CODE_MISSING, code);
            }
            Objects.requireNonNull(valueType, "valueType");
        }
    }

    /** {@code catalog.quality.severity.QP-8 = blocking} — the {@code strict-media} variant of 08 as a value. */
    record SeverityOverride(QualityFindings.Rule rule, QualityFindings.Severity severity) {

        SeverityOverride {
            Objects.requireNonNull(rule, "rule");
            Objects.requireNonNull(severity, "severity");
        }
    }
}
