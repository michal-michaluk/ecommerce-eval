package shop.catalog.quality;

import org.springframework.boot.context.properties.ConfigurationProperties;
import shop.catalog.offer.ProductDescription.Parameter.ValueType;
import shop.catalog.quality.QualityFindings.Rule;
import shop.catalog.quality.QualityFindings.Severity;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * {@code catalog.quality.*} of 08 as bound properties: the names are the documented keys and nothing else, so an operator reads
 * the same words in {@code application.yml} and in this type (relaxed binding makes {@code min-prose-chars} and
 * {@code severity.QP-8} bind).
 *
 * <p>{@code required-parameters} is a list of {@code {prefix, parameters}} and never a map keyed by the prefix: a category path is
 * a list, and a YAML map key cannot carry one (D33). Nothing is interpreted here — {@link QualityConfigAdapter} is the single
 * place that turns these properties into the value {@link QualityPolicy} takes.
 *
 * <p>Three keys are optional by 08's own wording. {@code min-prose-chars} defaults to {@link
 * QualityConfig#DEFAULT_MIN_PROSE_CHARS}. {@code severity} is absent by default, because 08 states "absent = 08's table"; an
 * unknown rule id or severity word fails binding, as 08's category-rules section requires — a severity nobody reads is a
 * silently misconfigured policy. {@code grammar.enabled} binds and carries no behaviour: QP-9 needs an external grammar service
 * that is out of scope, so the key stays reserved (D29) and has no counterpart in the value.
 */
@ConfigurationProperties(prefix = "catalog.quality")
record QualityProperties(
        Integer minProseChars,
        Map<Rule, Severity> severity,
        Grammar grammar,
        List<CategoryRule> requiredParameters) {

    QualityProperties {
        minProseChars = minProseChars == null ? QualityConfig.DEFAULT_MIN_PROSE_CHARS : minProseChars;
        severity = severity == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(severity));
        grammar = grammar == null ? new Grammar(false) : grammar;
        requiredParameters = requiredParameters == null ? List.of() : List.copyOf(requiredParameters);
    }

    /** {@code catalog.quality.grammar.enabled} — the reserved {@code grammar-enabled} variant of 08, bound and unimplemented. */
    record Grammar(boolean enabled) {
    }

    /** One {@code required-parameters} entry: the category path and its parameters, whose order is QP-10…QP-12's report order. */
    record CategoryRule(List<String> prefix, Map<String, Parameter> parameters) {

        CategoryRule {
            prefix = prefix == null ? List.of() : List.copyOf(prefix);
            parameters = parameters == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(parameters));
        }
    }

    /**
     * One configured parameter: the value type QP-11 compares and whether QP-10 reports it when absent.
     *
     * <p>{@code required} has no default in 08, so nothing is defaulted to {@code true} here: an omitted key is {@code false},
     * which in {@link QualityConfig.RequiredParameter} means exactly what it says — the category allows the parameter to be absent,
     * and QP-10 stays silent for it.
     */
    record Parameter(ValueType valueType, boolean required) {
    }
}
