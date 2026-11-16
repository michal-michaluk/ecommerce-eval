package devices.configuration.search;

import devices.configuration.communication.DeviceStatuses;
import devices.configuration.device.DeviceConfiguration;
import devices.configuration.device.Location;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.Optional;

@Schema(description = "Device as pinned on the map: identifier, coordinates and normalised statuses.")
record DevicePin(
        @Schema(description = "Identifier of the device.")
        String deviceId,
        @Schema(description = "Coordinates of the device.")
        Location.Coordinates coordinates,
        @Schema(description = "Normalised statuses of the device; `FAULTED` when no status is known.")
        List<Status> statuses) {

    @Schema(description = "Status of the device on the map.")
    enum Status {
        @Schema(description = "The device reported the status `Available`.")
        AVAILABLE,
        @Schema(description = "The device reported a status other than `Available` and `Faulted`.")
        CHARGING,
        @Schema(description = "The device reported the status `Faulted`.")
        FAULTED
    }

    static DevicePin ofNullable(DeviceConfiguration details, DeviceStatuses statuses) {
        if (details == null || details.location() == null) {
            return null;
        }
        return new DevicePin(
                details.deviceId(),
                details.location().coordinates(),
                Optional.ofNullable(statuses)
                        .map(s -> s.map(DevicePin::normalised))
                        .orElse(List.of(Status.FAULTED))
        );
    }

    private static Status normalised(String raw) {
        return switch (raw) {
            case "Available" -> Status.AVAILABLE;
            case "Faulted" -> Status.FAULTED;
            default -> Status.CHARGING;
        };
    }
}
