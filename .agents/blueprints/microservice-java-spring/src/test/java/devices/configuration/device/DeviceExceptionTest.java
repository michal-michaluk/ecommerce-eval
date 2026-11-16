package devices.configuration.device;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DeviceExceptionTest {

    @Test
    void valueObjectRejectsHalfOwnershipWithItsErrorType() {
        assertThatThrownBy(() -> Ownership.of("operator", null))
                .isInstanceOfSatisfying(DeviceException.class, failure -> {
                    assertThat(failure.error()).isEqualTo(DeviceException.Error.OWNERSHIP_INCOMPLETE);
                    assertThat(failure.details()).containsExactly("operator", null);
                    assertThat(failure).hasMessage("ownership must be complete or absent");
                });
    }

    @Test
    void commandNamesTheValueItRefusesToAcceptAsMissing() {
        DeviceConfigurationEditor device = DeviceConfigurationEditor.newDevice("device-1");

        assertThatThrownBy(() -> device.assignTo(null))
                .isInstanceOfSatisfying(DeviceException.class, failure -> {
                    assertThat(failure.error()).isEqualTo(DeviceException.Error.COMMAND_VALUE_MISSING);
                    assertThat(failure.details()).containsExactly("ownership");
                    assertThat(failure).hasMessage("ownership is required for this command");
                });
    }
}
