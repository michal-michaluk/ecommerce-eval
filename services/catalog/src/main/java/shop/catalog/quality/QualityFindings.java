package shop.catalog.quality;

import shop.catalog.offer.ProductDescription.Parameter.ValueType;

import java.util.List;

/**
 * Element 08's answer — the outcome of one {@link QualityPolicy} call as a value: the findings in report order, the prose the
 * policy measured, and the configured category prefix whose rules were applied (empty when none matched).
 *
 * <p>Findings are ordered by rule number and, inside one rule, by the configuration's declaration order, which is exactly the
 * order a reviewer reads them in. {@code blocking} is what 09 turns into a rejection; a draft carrying warnings only stays
 * publishable (S18).
 *
 * <p>It is public together with {@link Finding}, {@link Rule} and {@link Severity} because the mediator of the {@code review →
 * quality} edge names them to answer 04's port (D42, 17 RULE-1).
 */
public record QualityFindings(List<Finding> findings, int prose, List<String> matchedCategory) {

    public QualityFindings {
        findings = List.copyOf(findings);
        matchedCategory = List.copyOf(matchedCategory);
    }

    /** Warnings never block: 09 reads the blocking findings only, so an unrejected warning leaves the draft publishable. */
    boolean publishable() {
        return findings.stream().noneMatch(Finding::isBlocking);
    }

    /** The two severities of 08; a rule's default comes from the table, {@link QualityConfig} may override it per rule. */
    public enum Severity {
        BLOCKING,
        WARNING
    }

    /**
     * The rules of 08 in report order, each with the severity its own table row declares.
     *
     * <p>QP-9 (grammar and correctness validation) is deliberately absent: it needs a new external technology, so it is reserved
     * as the unimplemented {@code grammar-enabled} variant and carries neither a constant nor a dead check.
     */
    public enum Rule {
        QP_1(1, Severity.BLOCKING),
        QP_2(2, Severity.BLOCKING),
        QP_3(3, Severity.BLOCKING),
        QP_4(4, Severity.BLOCKING),
        QP_5(5, Severity.BLOCKING),
        QP_6(6, Severity.BLOCKING),
        QP_7(7, Severity.BLOCKING),
        QP_8(8, Severity.WARNING),
        QP_10(10, Severity.BLOCKING),
        QP_11(11, Severity.BLOCKING),
        QP_12(12, Severity.WARNING);

        private final int number;
        private final Severity severity;

        Rule(int number, Severity severity) {
            this.number = number;
            this.severity = severity;
        }

        int number() {
            return number;
        }

        /** The published label of the rule as 08's table and every message name it, {@code QP-8} for {@link #QP_8}. */
        public String label() {
            return "QP-" + number;
        }

        Severity severity() {
            return severity;
        }
    }

    /** One finding: the rule, the draft field it names, the severity in force and the message a reviewer reads. */
    public record Finding(Rule rule, String field, Severity severity, String message) {

        static Finding titleNotAccepted(String title, int maxLength) {
            return of(Rule.QP_1, "description.title",
                    "title must be present, trimmed and 1-%s characters, got '%s'", maxLength, title);
        }

        static Finding eanGtinNotAccepted(String eanGtin) {
            return of(Rule.QP_2, "description.ids.eanGtin",
                    "eanGtin must be 13 digits with a valid GTIN-13 check digit, got '%s'", eanGtin);
        }

        static Finding productNumberMissing() {
            return of(Rule.QP_3, "description.ids.productNumber", "productNumber is required");
        }

        static Finding imageMediaMissing() {
            return of(Rule.QP_4, "media", "at least one media item must be an image");
        }

        static Finding primaryMediaAmbiguous(long primaryItems) {
            return of(Rule.QP_5, "media.primary", "exactly one media item must be primary, got %s", primaryItems);
        }

        static Finding proseTooShort(int prose, int minProseChars) {
            return of(Rule.QP_6, "description.blocks", "prose %s < %s (minProseChars)", prose, minProseChars);
        }

        static Finding paragraphMissing() {
            return of(Rule.QP_6, "description.blocks",
                    "at least one paragraph block is required: prose counts paragraphs and bullet lists only");
        }

        static Finding mediaReferenceUnknown(int blockNumber, String mediaId) {
            return of(Rule.QP_7, "description.blocks",
                    "image block %s references unknown media id '%s'", blockNumber, mediaId);
        }

        static Finding mediaPositionsNotContiguous(List<Integer> positions) {
            return of(Rule.QP_8, "media", "media positions must be contiguous from 1, got %s", positions);
        }

        static Finding tooManyVideos(long videos) {
            return of(Rule.QP_8, "media", "at most one video is allowed, got %s", videos);
        }

        static Finding requiredParameterMissing(String code, String category) {
            return of(Rule.QP_10, parameterField(code), "required parameter missing for category %s", category);
        }

        static Finding parameterValueTypeMismatch(String code, ValueType expected, ValueType actual) {
            return of(Rule.QP_11, parameterField(code),
                    "parameter '%s' declares value type %s, the category expects %s", code, actual, expected);
        }

        static Finding parameterNotConfigured(String code, String category) {
            return of(Rule.QP_12, parameterField(code),
                    "parameter '%s' is not configured for category %s", code, category);
        }

        boolean isBlocking() {
            return severity == Severity.BLOCKING;
        }

        /** The same finding under the severity the configuration puts in force for its rule. */
        Finding withSeverity(Severity configured) {
            return new Finding(rule, field, configured, message);
        }

        private static String parameterField(String code) {
            return "parameters." + code;
        }

        private static Finding of(Rule rule, String field, String template, Object... details) {
            return new Finding(rule, field, rule.severity(), template.formatted(details));
        }
    }
}
