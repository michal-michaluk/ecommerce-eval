package devices.configuration.device;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.time.LocalTime;
import java.util.List;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

@Schema(description = "Opening hours of a device: either always open, or opening times per weekday.")
record OpeningHours(
        @Schema(description = "Whether the device is open around the clock.")
        boolean alwaysOpen,
        @Schema(description = "Opening times per weekday; omitted when the device is always open.")
        @JsonInclude(NON_NULL) Week opened) {

    private final static OpeningHours ALWAYS_OPEN = new OpeningHours(true, null);

    public static OpeningHours alwaysOpened() {
        return ALWAYS_OPEN;
    }

    public static OpeningHours openAt(Week week) {
        return new OpeningHours(false, week);
    }

    @Builder
    @Schema(description = "Opening time of each weekday.")
    record Week(
            @Schema(description = "Opening time on Monday.")
            OpeningTime monday,
            @Schema(description = "Opening time on Tuesday.")
            OpeningTime tuesday,
            @Schema(description = "Opening time on Wednesday.")
            OpeningTime wednesday,
            @Schema(description = "Opening time on Thursday.")
            OpeningTime thursday,
            @Schema(description = "Opening time on Friday.")
            OpeningTime friday,
            @Schema(description = "Opening time on Saturday.")
            OpeningTime saturday,
            @Schema(description = "Opening time on Sunday.")
            OpeningTime sunday) {
    }

    @JsonTypeInfo(use = JsonTypeInfo.Id.MINIMAL_CLASS)
    @Schema(description = "Opening time of one day; polymorphic, so the JSON carries a class "
                          + "discriminator giving the concrete kind: open around the clock, closed "
                          + "around the clock, or a list of time spans.")
    sealed interface OpeningTime {
        @Schema(description = "Open around the clock.")
        record Opened24h() implements OpeningTime {
        }

        @Schema(description = "Closed around the clock.")
        record Closed24h() implements OpeningTime {
        }

        @Schema(description = "Open only within the listed time spans.")
        record OpenTime(
                @Schema(description = "Time spans during which the device is open.")
                List<TimeSpan> time) implements OpeningTime {
        }

        @Schema(description = "Time span of one day.")
        record TimeSpan(
                @Schema(description = "Start of the time span, local time.")
                LocalTime open,
                @Schema(description = "End of the time span, local time.")
                LocalTime close) {
        }

        static OpeningTime closed24h() {
            return new Opened24h();
        }

        static OpeningTime opened24h() {
            return new Closed24h();
        }

        static OpeningTime opened(int open, int close) {
            return new OpenTime(List.of(new TimeSpan(
                    LocalTime.of(open, 0),
                    LocalTime.of(close, 0)
            )));
        }
    }
}
