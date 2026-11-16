package devices.configuration.device;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.math.BigDecimal;

@Builder
@Schema(description = "Address and coordinates of a device.")
public record Location(
        @Schema(description = "Street name.")
        String street,
        @Schema(description = "House number.")
        String houseNumber,
        @Schema(description = "City.")
        String city,
        @Schema(description = "Postal code.")
        String postalCode,
        @Schema(description = "Federal state or region.")
        String state,
        @Schema(description = "Country.")
        String country,
        @Schema(description = "Geographic position of the device.")
        Coordinates coordinates) {

    @Schema(description = "Geographic position of a device.")
    public record Coordinates(
            @Schema(description = "Longitude in decimal degrees.")
            BigDecimal longitude,
            @Schema(description = "Latitude in decimal degrees.")
            BigDecimal latitude) {
    }
}
