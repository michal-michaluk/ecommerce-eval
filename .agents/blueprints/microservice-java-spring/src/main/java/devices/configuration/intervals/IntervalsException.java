package devices.configuration.intervals;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * The only exception the intervals context raises. An unchecked failure carries the error type — which owns the message — and the
 * domain values a caller or an adapter may need, so no caller matches on a message string and no domain error needs its own class.
 */
class IntervalsException extends RuntimeException {

    enum Error {
        CONFIGURATION_UNREADABLE("configuration %s could not be read"),
        INTEGRATION_FAILED("%s did not answer");

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

    IntervalsException(Error error, Object... details) {
        super(error.format(details));
        this.error = error;
        this.details = Collections.unmodifiableList(Arrays.asList(details));
    }

    IntervalsException(Error error, Throwable cause, Object... details) {
        super(error.format(details), cause);
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
