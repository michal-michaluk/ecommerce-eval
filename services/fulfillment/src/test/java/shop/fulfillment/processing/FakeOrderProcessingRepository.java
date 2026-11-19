package shop.fulfillment.processing;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import shop.fulfillment.kernel.OrderProcessingId;

/**
 * The {@link OrderProcessingRepository} port backed by a map: the same find-or-create lifecycle the adapter has, without a
 * database, so a service test asserts the use case and not the storage.
 *
 * <p>It mirrors the two facts a test needs to see — what was stored and how many writes the use case made — and it drains
 * nothing: the aggregate keeps its event buffer, so a test can read the events the use case emitted.
 */
class FakeOrderProcessingRepository implements OrderProcessingRepository {

    final Map<String, OrderFulfillmentProcess> processings = new LinkedHashMap<>();
    final List<String> saved = new ArrayList<>();
    private int minted;

    @Override
    public Optional<OrderFulfillmentProcess> get(String processingId) {
        return Optional.ofNullable(processings.get(processingId));
    }

    @Override
    public Optional<OrderFulfillmentProcess> findByOrderHash(String orderHash) {
        return processings.values().stream()
                .filter(processing -> orderHash.equals(processing.toSnapshot().orderHash()))
                .findFirst();
    }

    @Override
    public OrderProcessingId nextIdentity() {
        return new OrderProcessingId("OP-2026-%04d".formatted(++minted));
    }

    @Override
    public void save(OrderFulfillmentProcess processing) {
        processings.put(processing.processingId.value(), processing);
        saved.add(processing.processingId.value());
    }

    void store(OrderFulfillmentProcess processing) {
        processings.put(processing.processingId.value(), processing);
    }

    OrderFulfillmentProcess stored(OrderProcessingId processingId) {
        return processings.get(processingId.value());
    }

    List<OrderFulfillmentProcess> all() {
        return List.copyOf(processings.values());
    }
}
