package devices.configuration.device;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Whether roaming is enabled and under which category customers see the device.")
record Visibility(
        @Schema(description = "Whether roaming is enabled for the device.")
        boolean roamingEnabled,
        @Schema(description = "Category under which customers see the device.")
        ForCustomer forCustomer) {

    static Visibility calculateVisibility(boolean usable, boolean showOnMap) {
        return new Visibility(usable, ForCustomer.calculateForCustomer(usable, showOnMap));
    }

    @Schema(description = "Category of the device as seen by customers.")
    enum ForCustomer {
        @Schema(description = "The device is usable and shown on the map.")
        USABLE_AND_VISIBLE_ON_MAP,
        @Schema(description = "The device is usable but hidden from the map.")
        USABLE_BUT_HIDDEN_ON_MAP,
        @Schema(description = "The device is not usable and hidden from the map.")
        INACCESSIBLE_AND_HIDDEN_ON_MAP;

        private static ForCustomer calculateForCustomer(boolean usable, boolean showOnMap) {
            if (!usable) {
                return ForCustomer.INACCESSIBLE_AND_HIDDEN_ON_MAP;
            } else if (showOnMap) {
                return ForCustomer.USABLE_AND_VISIBLE_ON_MAP;
            } else {
                return ForCustomer.USABLE_BUT_HIDDEN_ON_MAP;
            }
        }
    }
}
