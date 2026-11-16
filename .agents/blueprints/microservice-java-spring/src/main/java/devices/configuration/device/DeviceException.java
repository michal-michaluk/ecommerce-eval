package devices.configuration.device;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * The only exception the device context raises. An unchecked failure carries the error type — which owns the message — and the
 * domain values a caller or an adapter may need, so no caller matches on a message string and no domain error needs its own class.
 */
class DeviceException extends RuntimeException {

    enum Error {
        OWNERSHIP_INCOMPLETE("ownership must be complete or absent"),
        COMMAND_VALUE_MISSING("%s is required for this command"),
        DOCUMENT_UNREADABLE("stored document %s could not be read");

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

    DeviceException(Error error, Object... details) {
        super(error.format(details));
        this.error = error;
        this.details = Collections.unmodifiableList(Arrays.asList(details));
    }

    DeviceException(Error error, Throwable cause, Object... details) {
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
