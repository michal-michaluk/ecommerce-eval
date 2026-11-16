package devices.configuration.search;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import devices.configuration.communication.BootNotification;
import devices.configuration.device.DeviceConfiguration;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Stored configuration of the device together with its last boot notification.")
record DeviceDetails(
        @Schema(description = "Configuration of the device, flattened into this object by @JsonUnwrapped; no `configuration` property is serialised.")
        @JsonUnwrapped
        DeviceConfiguration configuration,
        @Schema(description = "Last boot notification stored for the device.")
        BootNotification boot) {
}
