package shop.fulfillment.customer;

import shop.fulfillment.kernel.CollectionHandle;
import shop.fulfillment.kernel.DomainEvent;
import shop.fulfillment.kernel.Leg;
import shop.fulfillment.kernel.LegReached;
import shop.fulfillment.kernel.OrderFixture;
import shop.fulfillment.kernel.OrderProcessingId;
import shop.fulfillment.kernel.ProcessingFixture;
import shop.fulfillment.kernel.ProcessingState;

/**
 * The facts the customer view is fed with, built once: one handover, one claim, one hold, one readiness and the delivery legs a
 * carrier reports — so a test states the history it wants instead of the payloads it has to spell.
 */
public class CustomerFixture {

    private CustomerFixture() {
    }

    public static DomainEvent.OrderProcessingStarted started(OrderProcessingId processingId) {
        return new DomainEvent.OrderProcessingStarted(processingId, OrderFixture.anOrder());
    }

    public static DomainEvent.EmployeeAssigned assigned(OrderProcessingId processingId) {
        return new DomainEvent.EmployeeAssigned(processingId, ProcessingFixture.EMPLOYEE);
    }

    public static DomainEvent.PositionPicked picked(OrderProcessingId processingId) {
        return new DomainEvent.PositionPicked(processingId, OrderFixture.PRODUCT_NUMBER);
    }

    public static DomainEvent.PositionPacked packed(OrderProcessingId processingId) {
        return new DomainEvent.PositionPacked(processingId, OrderFixture.PRODUCT_NUMBER);
    }

    public static DomainEvent.HoldPlaced held(OrderProcessingId processingId) {
        return new DomainEvent.HoldPlaced(processingId, ProcessingFixture.REPORTED_AT);
    }

    public static DomainEvent.OrderResumed resumed(OrderProcessingId processingId, ProcessingState state) {
        return new DomainEvent.OrderResumed(processingId, state);
    }

    public static DomainEvent.ReadyForDeliveryConfirmed ready(OrderProcessingId processingId) {
        return new DomainEvent.ReadyForDeliveryConfirmed(processingId, ProcessingFixture.PARCEL_ID);
    }

    public static DomainEvent.OrderDelivered delivered(OrderProcessingId processingId) {
        return new DomainEvent.OrderDelivered(processingId);
    }

    public static DomainEvent.DeliveryStateNotified notified(Leg leg, String carrierStatus) {
        return new DomainEvent.DeliveryStateNotified(ProcessingFixture.PARCEL_ID, leg, carrierStatus);
    }

    public static DomainEvent.DeliveryNumberGenerated numberGenerated() {
        return new DomainEvent.DeliveryNumberGenerated(ProcessingFixture.PARCEL_ID,
                shop.fulfillment.kernel.CarrierFixture.IDENTITY);
    }

    public static DomainEvent.CollectionRequested collectionRequested() {
        return new DomainEvent.CollectionRequested(ProcessingFixture.PARCEL_ID, new CollectionHandle("COL-0001"));
    }

    static LegReached aLeg(Leg leg) {
        return new LegReached(leg, shop.fulfillment.kernel.CarrierFixture.REACHED_AT);
    }
}
