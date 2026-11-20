package shop.fulfillment.kernel;

import java.time.Instant;

/**
 * One delivery leg that was reached, with the moment it was recorded — the parcel's own history, oldest first.
 */
public record LegReached(Leg leg, Instant at) {
}
