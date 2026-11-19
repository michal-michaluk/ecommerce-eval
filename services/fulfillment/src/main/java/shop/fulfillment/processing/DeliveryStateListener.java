package shop.fulfillment.processing;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import shop.fulfillment.kernel.DomainEvent;

/**
 * The order's side of {@code delivery state notification}: the leg the carrier reported, consumed by the aggregate that owns the
 * order's state (element 02, "consumed from neighbours").
 *
 * <p>The status poll of the parcel slice raises the event after it stored the leg, so this listener runs with the parcel
 * already written and in the same transaction: either the parcel and the order both learn the leg, or neither does. Only the
 * last leg moves anything — {@link OrderFulfillmentProcess#applyDeliveryState} ignores L1–L5, which live on the parcel alone —
 * and a leg reported for a parcel no processing carries (an order not yet marked ready) is a fact this slice has nothing to do
 * with: it is ignored, never an error ({@code mapping.listener-no-invariant}).
 */
@Component
@Transactional
class DeliveryStateListener {

    private final OrderProcessingRepository processings;

    DeliveryStateListener(OrderProcessingRepository processings) {
        this.processings = processings;
    }

    /**
     * Applies one carrier leg to the order that carries the parcel it was reported for.
     *
     * @param notified the leg the carrier reported
     */
    @EventListener
    public void handle(DomainEvent.DeliveryStateNotified notified) {
        processings.findByParcelId(notified.parcelId())
                .ifPresent(processing -> delivered(processing, notified));
    }

    private void delivered(OrderFulfillmentProcess processing, DomainEvent.DeliveryStateNotified notified) {
        processing.applyDeliveryState(notified.leg());
        if (!processing.events.isEmpty()) {
            processings.save(processing);
        }
    }
}
