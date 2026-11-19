package shop.fulfillment.processing;

import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Optional;

import shop.fulfillment.kernel.FulfillmentException;
import shop.fulfillment.kernel.WarehouseStock;

/**
 * The claim's warehouse side: what register D11 asks for on a granted claim, wired to whatever answers {@link WarehouseStock}
 * and to the transaction the claim runs in.
 *
 * <p>It is the one place that knows the allocation must not outlive a failed claim: the claim is written, the warehouse is
 * asked, and a transaction that ends any way but {@code committed} — a lost optimistic lock is detected exactly there, at
 * flush, after the allocation happened — releases every reservation it took. A warehouse that fails half way is compensated
 * immediately by {@link WarehouseReservations} itself.
 *
 * <p>A deployment with no warehouse adapter still boots and refuses the claim loudly: no stock is invented, no fallback
 * quantity is served (<code>warehouse-api.md</code>, element 13 RULE-7). The real adapter is its own spec (spec D2, D14).
 */
@Component
class WarehouseReservationsIntegration {

    private final Optional<WarehouseStock> warehouse;

    WarehouseReservationsIntegration(Optional<WarehouseStock> warehouse) {
        this.warehouse = warehouse;
    }

    /**
     * Reserves every position of the claimed order and arranges the release the claim's transaction may need.
     *
     * @param claimed the processing as the granted claim left it
     */
    void reserveClaim(OrderProcessing claimed) {
        WarehouseReservations reservations = WarehouseReservations.on(answeringWarehouse());
        releaseWhenTheTransactionFails(reservations);
        reservations.reserveEvery(claimed.positions());
    }

    private WarehouseStock answeringWarehouse() {
        return warehouse.orElseThrow(() -> new FulfillmentException(
                FulfillmentException.Error.WAREHOUSE_NOT_WIRED, "reserving a claim"));
    }

    private static void releaseWhenTheTransactionFails(WarehouseReservations reservations) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new ReleaseOnFailure(reservations));
        }
    }

    /** The compensation of one claim: everything it allocated goes back unless the transaction committed. */
    static final class ReleaseOnFailure implements TransactionSynchronization {

        private final WarehouseReservations reservations;

        ReleaseOnFailure(WarehouseReservations reservations) {
            this.reservations = reservations;
        }

        @Override
        public void afterCompletion(int status) {
            if (status != STATUS_COMMITTED) {
                reservations.releaseAll();
            }
        }
    }
}
