package shop.fulfillment.parcel;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

import shop.fulfillment.kernel.CarrierPort;

/**
 * The delivery legs of element 03 as one scheduled job: read what the carrier reports for every parcel still on its way,
 * apply it to the parcel, and let the adapter publish the newly stored legs.
 *
 * <p>The first adapter publishes no push mechanism, so the legs of the board's {@code delivery state notification} are
 * something we read rather than something we receive (element 14). The read is idempotent: the same notification applied
 * twice changes nothing, because the aggregate's key is {@code (waybill, leg)} and it never moves backwards — so re-polling
 * an overlapping window costs nothing and a poll that failed is simply repeated.
 *
 * <p>Failure is surfaced, never smoothed: no retry, no backoff and no invented fallback, the scheduled task's own error
 * handler logs the failure and the next tick reads the same legs again (element 14, element 03 RULE-4). A deployment with no
 * carrier has nothing to poll and does nothing.
 *
 * <p>The interval is {@code fulfillment.carrier.poll-interval}, default {@value #DEFAULT_POLL_INTERVAL}: no source states a
 * number, and the choice is recorded in {@code docs/phase-1-decisions.md} — our polling interval is the delivery-state latency
 * the customer sees (element 18 R-18-1).
 */
@Component
class CarrierPollingIntegration {

    static final String DEFAULT_POLL_INTERVAL = "60s";

    private final Optional<CarrierPort> carrier;
    private final ParcelRepository parcels;

    CarrierPollingIntegration(Optional<CarrierPort> carrier, ParcelRepository parcels) {
        this.carrier = carrier;
        this.parcels = parcels;
    }

    /**
     * One poll: every parcel the carrier has not delivered yet, its reported legs applied forward only.
     */
    @Scheduled(fixedDelayString = "${fulfillment.carrier.poll-interval:" + DEFAULT_POLL_INTERVAL + "}",
            initialDelayString = "${fulfillment.carrier.poll-interval:" + DEFAULT_POLL_INTERVAL + "}")
    @Transactional
    void poll() {
        carrier.ifPresent(this::read);
    }

    private void read(CarrierPort port) {
        parcels.awaitingDelivery().forEach(parcel -> read(port, parcel));
    }

    /** Applies every leg the carrier reports, then stores what changed — a poll that learned nothing writes nothing. */
    private void read(CarrierPort port, ParcelEditor parcel) {
        port.status(parcel.toParcelState().carrierIdentity()).forEach(parcel::applyNotification);
        if (!parcel.events.isEmpty()) {
            parcels.save(parcel);
        }
    }
}
