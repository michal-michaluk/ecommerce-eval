package devices.configuration.search;

import devices.configuration.communication.DeviceStatuses;
import devices.configuration.device.DeviceConfiguration;
import devices.configuration.device.Location;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.Optional;

@Schema(description = "Device as listed for the map: identifier, location and statuses.")
record DeviceSummary(
        @Schema(description = "Identifier of the device.")
        String deviceId,
        @Schema(description = "Location of the device.")
        Location location,
        @Schema(description = "Statuses reported by the device; `Faulted` when no status is known.")
        List<String> statuses) {

    static DeviceSummary ofNullable(DeviceConfiguration details, DeviceStatuses statuses) {
        if (details == null || details.location() == null) {
            return null;
        }
        return new DeviceSummary(
                details.deviceId(),
                details.location(),
                Optional.ofNullable(statuses)
                        .map(DeviceStatuses::statuses)
                        .orElse(List.of("Faulted")));
    }
}
