package shop.catalog.quality;

import org.assertj.core.api.Assertions;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import shop.catalog.quality.QualityFindings.Finding;
import shop.catalog.quality.QualityFindings.Rule;
import shop.catalog.quality.QualityFindings.Severity;

import java.util.List;

/** The assertions of 08: report order, the field and severity of each finding, the message, the measured prose and publishability. */
public class QualityAssert {

    private final QualityFindings findings;

    private QualityAssert(QualityFindings findings) {
        this.findings = findings;
    }

    public static QualityAssert assertThat(QualityFindings actual) {
        Assertions.assertThat(actual).isNotNull();
        return new QualityAssert(actual);
    }

    public static void assertRefused(QualityException.Error expected, ThrowingCallable call) {
        Assertions.assertThatThrownBy(call)
                .isInstanceOfSatisfying(QualityException.class,
                        refused -> Assertions.assertThat(refused.error()).isEqualTo(expected));
    }

    public QualityAssert hasNoFindings() {
        Assertions.assertThat(findings.findings()).isEmpty();
        return this;
    }

    public QualityAssert hasRules(Rule... expected) {
        Assertions.assertThat(findings.findings()).extracting(Finding::rule).containsExactly(expected);
        return this;
    }

    public QualityAssert hasFields(String... expected) {
        Assertions.assertThat(findings.findings()).extracting(Finding::field).containsExactly(expected);
        return this;
    }

    public QualityAssert hasMessages(String... expected) {
        Assertions.assertThat(findings.findings()).extracting(Finding::message).containsExactly(expected);
        return this;
    }

    public QualityAssert hasSeverities(Severity... expected) {
        Assertions.assertThat(findings.findings()).extracting(Finding::severity).containsExactly(expected);
        return this;
    }

    public QualityAssert hasOnlyFinding(Rule rule, Severity severity, String field) {
        return hasRules(rule).hasSeverities(severity).hasFields(field);
    }

    public QualityAssert hasProse(int expected) {
        Assertions.assertThat(findings.prose()).isEqualTo(expected);
        return this;
    }

    public QualityAssert hasMatchedCategory(List<String> expected) {
        Assertions.assertThat(findings.matchedCategory()).containsExactlyElementsOf(expected);
        return this;
    }

    public QualityAssert hasNoMatchedCategory() {
        Assertions.assertThat(findings.matchedCategory()).isEmpty();
        return this;
    }

    public QualityAssert isPublishable() {
        Assertions.assertThat(findings.publishable()).isTrue();
        return this;
    }

    public QualityAssert isNotPublishable() {
        Assertions.assertThat(findings.publishable()).isFalse();
        return this;
    }
}
