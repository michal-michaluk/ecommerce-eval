package shop.fulfillment.processing;

import java.time.Instant;

/**
 * One leg the carrier reports, mapped onto our own six names, with the carrier's raw status and raw payload kept as
 * they arrived so a mapping can be corrected later.
 *
 * <p>An unmapped carrier status is a value, not an error: it lands in an observation with no {@code leg}, which moves
 * nothing and only stores the payload. The port standardises on our legs — a carrier's own codes live and die in its
 * adapter.
 *
 * @param leg              the leg we recognised, or {@code null} when the status could not be mapped
 * @param carrierTimestamp the moment the carrier reports for the leg
 * @param carrierStatus    the carrier's status code or text, unread by this context
 * @param rawPayload       the carrier's payload, stored verbatim
 */
public record LegObservation(Leg leg, Instant carrierTimestamp, String carrierStatus, String rawPayload) {

    public LegObservation {
        if (carrierTimestamp == null || carrierStatus == null || carrierStatus.isBlank()) {
            throw new FulfillmentException(FulfillmentException.Error.COMMAND_VALUE_MISSING, "legObservation");
        }
    }

    /**
     * A carrier status this context could not map to one of its own legs.
     *
     * @param carrierTimestamp the moment the carrier reports
     * @param carrierStatus    the carrier's status code or text
     * @param rawPayload       the carrier's payload, stored verbatim
     * @return an observation that moves nothing
     */
    public static LegObservation unmapped(Instant carrierTimestamp, String carrierStatus, String rawPayload) {
        return new LegObservation(null, carrierTimestamp, carrierStatus, rawPayload);
    }
}
