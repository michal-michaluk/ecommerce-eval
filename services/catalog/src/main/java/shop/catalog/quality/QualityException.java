package shop.catalog.quality;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * The only exception the quality context raises; the error type owns the message and carries the offending values.
 *
 * <p>{@link QualityPolicy} itself never throws — an incomplete draft is what it reports. Only the configuration of 08 refuses a
 * value it cannot interpret, and that refusal arrives before any draft is evaluated. There is one constructor because nothing in
 * this context can fail for a cause: a cause-taking overload arrives with the first adapter that reads a stored document.
 */
class QualityException extends RuntimeException {

    enum Error {
        MIN_PROSE_CHARS_NOT_POSITIVE("minProseChars must be positive, got %s"),
        CATEGORY_PREFIX_MISSING("a configured category must name its prefix as at least one category"),
        PARAMETER_CODE_MISSING("a configured parameter must name its code, got '%s'"),
        SEVERITY_DECLARED_TWICE("a rule's severity may be overridden once, %s is declared more than once");

        private final String template;

        Error(String template) {
            this.template = template;
        }

        String format(Object... details) {
            return template.formatted(details);
        }
    }

    private final Error error;
    private final List<Object> details;

    QualityException(Error error, Object... details) {
        super(error.format(details));
        this.error = error;
        this.details = Collections.unmodifiableList(Arrays.asList(details));
    }

    Error error() {
        return error;
    }

    List<Object> details() {
        return details;
    }
}
