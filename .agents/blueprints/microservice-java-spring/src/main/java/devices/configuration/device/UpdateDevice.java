package devices.configuration.device;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import jakarta.validation.Valid;

@Builder
@Schema(description = "Configuration sections to apply to a device; absent sections are left unchanged.")
public record UpdateDevice(
        @Schema(description = "New location of the device; omitted when the location is not updated.")
        @Valid Location location,
        @Schema(description = "New opening hours; omitted when the opening hours are not updated.")
        @Valid OpeningHours openingHours,
        @Schema(description = "Settings fields to overwrite; fields left out keep their current value.")
        @Valid Settings settings,
        @Schema(description = "New operator and provider assignment; omitted when the assignment is not updated.")
        @Valid Ownership ownership) {

    public static UpdateDevice use(Ownership ownership, Location location) {
        return builder()
                .location(location)
                .ownership(ownership)
                .build();
    }

    public void apply(DeviceConfigurationEditor device) {
        if (location != null) {
            device.updateLocation(location);
        }
        if (openingHours != null) {
            device.updateOpeningHours(openingHours);
        }
        if (settings != null) {
            device.updateSettings(settings);
        }
        if (ownership != null) {
            device.assignTo(ownership);
        }
    }
}
