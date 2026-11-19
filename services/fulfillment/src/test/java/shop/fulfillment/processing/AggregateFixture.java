package shop.fulfillment.processing;

import shop.fulfillment.kernel.Order;
import shop.fulfillment.kernel.OrderFixture;
import shop.fulfillment.kernel.ProcessingFixture;

/**
 * The aggregate in the states the commands are decided on — each one reached through the commands themselves, never by
 * writing a field, so a test precondition is a legal history of the aggregate.
 */
public class AggregateFixture {

    private AggregateFixture() {
    }

    public static OrderFulfillmentProcess aPendingProcessing() {
        return OrderFulfillmentProcess.startProcessing(ProcessingFixture.PROCESSING_ID, OrderFixture.anOrder());
    }

    public static OrderFulfillmentProcess pendingProcessingOf(Order order) {
        return pendingProcessingAs(ProcessingFixture.PROCESSING_ID, order);
    }

    public static OrderFulfillmentProcess pendingProcessingAs(
            shop.fulfillment.kernel.OrderProcessingId processingId, Order order) {
        return OrderFulfillmentProcess.startProcessing(processingId, order);
    }

    public static OrderFulfillmentProcess anAssignedProcessing() {
        OrderFulfillmentProcess processing = aPendingProcessing();
        processing.assignTo(ProcessingFixture.EMPLOYEE);
        return processing;
    }

    public static OrderFulfillmentProcess aPickingProcessing() {
        OrderFulfillmentProcess processing = anAssignedProcessing();
        processing.pick(OrderFixture.PRODUCT_NUMBER);
        return processing;
    }

    public static OrderFulfillmentProcess aPackedProcessing() {
        OrderFulfillmentProcess processing = aPickingProcessing();
        processing.pack(OrderFixture.PRODUCT_NUMBER);
        return processing;
    }

    public static OrderFulfillmentProcess aReadyProcessing() {
        OrderFulfillmentProcess processing = aPackedProcessing();
        processing.markReady(ProcessingFixture.PARCEL_ID);
        return processing;
    }

    public static OrderFulfillmentProcess aDeliveredProcessing() {
        OrderFulfillmentProcess processing = aReadyProcessing();
        processing.applyDeliveryState(shop.fulfillment.kernel.Leg.L6);
        return processing;
    }

    public static OrderFulfillmentProcess aHeldProcessing() {
        OrderFulfillmentProcess processing = aPickingProcessing();
        processing.reportIssue(ProcessingFixture.anIssue());
        return processing;
    }
}
