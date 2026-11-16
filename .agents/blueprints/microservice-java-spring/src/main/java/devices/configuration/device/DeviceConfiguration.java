package devices.configuration.device;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

@Builder
@Schema(description = "Stored configuration of a device.")
public record DeviceConfiguration(
        @Schema(description = "Identifier of the device.")
        String deviceId,
        @Schema(description = "Operator and provider the device is assigned to.")
        Ownership ownership,
        @Schema(description = "Address and coordinates of the device.")
        Location location,
        @Schema(description = "Opening hours of the device.")
        OpeningHours openingHours,
        @Schema(description = "Feature flags configured for the device.")
        Settings settings,
        @Schema(description = "Configuration rules the device currently violates.")
        Violations violations,
        @Schema(description = "Visibility of the device to customers.")
        Visibility visibility) {
}
