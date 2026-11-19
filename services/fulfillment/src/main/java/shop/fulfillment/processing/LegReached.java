package shop.fulfillment.processing;

import java.time.Instant;

/**
 * One delivery leg that was reached, with the moment it was recorded — the parcel's own history, oldest first.
 */
record LegReached(Leg leg, Instant at) {
}
