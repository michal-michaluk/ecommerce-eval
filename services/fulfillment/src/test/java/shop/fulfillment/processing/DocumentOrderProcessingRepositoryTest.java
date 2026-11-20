package shop.fulfillment.processing;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.PersistenceException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import shop.fulfillment.IntegrationTest;
import shop.fulfillment.RecordedEvents;
import shop.fulfillment.TestTransaction;
import shop.fulfillment.kernel.DomainEvent;
import shop.fulfillment.kernel.ProcessingFixture;
import shop.fulfillment.kernel.ProcessingState;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The document-snapshot adapter of the process aggregate: one JSONB row survives the round trip with the order, the
 * positions and the state it was saved with, the idempotency key answers its processing, the identity sequence mints, and a
 * save publishes what the aggregate recorded — once.
 */
@IntegrationTest
class DocumentOrderProcessingRepositoryTest {

    static final shop.fulfillment.kernel.OrderProcessingId STALE = new shop.fulfillment.kernel.OrderProcessingId("OP-2026-9091");

    @Autowired
    DocumentOrderProcessingRepository processings;
    @Autowired
    DocumentOrderProcessingRepository.Processings rows;
    @Autowired
    RecordedEvents emitted;
    @Autowired
    EntityManager entityManager;
    @Autowired
    EntityManagerFactory entityManagerFactory;

    @Test
    void roundTripsTheProcessingThroughItsDocument() {
        saved(AggregateFixture.aPendingProcessing());

        OrderProcessing stored = processings.get(ProcessingFixture.PROCESSING_ID.value()).orElseThrow().toSnapshot();

        assertThat(stored.order()).isEqualTo(shop.fulfillment.kernel.OrderFixture.anOrder());
        assertThat(stored.state()).isEqualTo(ProcessingState.PENDING);
        assertThat(stored.positions()).singleElement()
                .satisfies(position -> assertThat(position.productNumber())
                        .isEqualTo(shop.fulfillment.kernel.OrderFixture.PRODUCT_NUMBER));
    }

    @Test
    void storesTheStateACommandLeftBehind() {
        saved(AggregateFixture.aPackedProcessing());

        OrderProcessing stored = processings.get(ProcessingFixture.PROCESSING_ID.value()).orElseThrow().toSnapshot();

        assertThat(stored.state()).isEqualTo(ProcessingState.PACKED);
        assertThat(stored.assignee()).isEqualTo(ProcessingFixture.EMPLOYEE);
    }

    @Test
    void storesTheHeldStateSoARestartResumesToIt() {
        saved(AggregateFixture.aHeldProcessing());

        OrderProcessing stored = processings.get(ProcessingFixture.PROCESSING_ID.value()).orElseThrow().toSnapshot();

        assertThat(stored.state()).isEqualTo(ProcessingState.ON_HOLD);
        assertThat(stored.heldState()).isEqualTo(ProcessingState.PICKING);
        assertThat(stored.issues()).hasSize(1);
    }

    @Test
    void answersTheProcessingOfOneOrderHash() {
        saved(AggregateFixture.aPendingProcessing());

        assertThat(processings.findByOrderHash(shop.fulfillment.kernel.OrderFixture.ORDER_HASH))
                .isPresent()
                .get()
                .satisfies(processing -> assertThat(processing.toSnapshot().processingId())
                        .isEqualTo(ProcessingFixture.PROCESSING_ID));
    }

    @Test
    void answersAnUnknownOrderHashWithNothing() {
        assertThat(processings.findByOrderHash("sha256:nothing")).isEmpty();
    }

    @Test
    void answersAnUnknownProcessingWithNothing() {
        assertThat(processings.get("OP-2026-9999")).isEmpty();
    }

    @Test
    void mintsOneProcessingIdentityPerDraw() {
        String first = processings.nextIdentity().value();
        String second = processings.nextIdentity().value();

        assertThat(first).matches("OP-\\d{4}-\\d{4}");
        assertThat(second).isNotEqualTo(first);
    }

    @Test
    void publishesTheEventsTheAggregateRecorded() {
        saved(AggregateFixture.anAssignedProcessing());

        assertThat(emitted.of(DomainEvent.EmployeeAssigned.class)).singleElement()
                .satisfies(event -> assertThat(event.assignee()).isEqualTo(ProcessingFixture.EMPLOYEE));
    }

    @Test
    void publishesNothingForASaveThatRecordedNothing() {
        saved(AggregateFixture.aPendingProcessing());
        saved(processings.get(ProcessingFixture.PROCESSING_ID.value()).orElseThrow());

        assertThat(emitted.of(DomainEvent.EmployeeAssigned.class)).isEmpty();
    }

    @Test
    void storesEveryPositionOfAMultiPositionOrder() {
        saved(AggregateFixture.pendingProcessingOf(shop.fulfillment.kernel.OrderFixture.twoLineOrder()));

        assertThat(processings.get(ProcessingFixture.PROCESSING_ID.value()).orElseThrow()
                .toSnapshot().positions())
                .extracting(shop.fulfillment.kernel.OrderPosition::productNumber)
                .containsExactly(shop.fulfillment.kernel.OrderFixture.PRODUCT_NUMBER, "99881");
    }

    @Test
    void keepsTheOrderContentTheDocumentWasWrittenWith() {
        saved(AggregateFixture.aPendingProcessing());

        assertThat(processings.get(ProcessingFixture.PROCESSING_ID.value()).orElseThrow()
                .toSnapshot().order().invoicing().buyerName())
                .isEqualTo("Jan Kowalski");
    }

    @Test
    void refusesAStaleWriteOfTheSameRow() {
        TestTransaction.transactional(() -> processings.save(AggregateFixture.pendingProcessingAs(STALE,
                shop.fulfillment.kernel.OrderFixture.anOrder("sha256:stale-write"))));
        EntityManager first = entityManagerFactory.createEntityManager();
        EntityManager second = entityManagerFactory.createEntityManager();
        try {
            DocumentOrderProcessingRepository.OrderProcessingEntity winner = beginAndLoad(first);
            DocumentOrderProcessingRepository.OrderProcessingEntity loser = beginAndLoad(second);
            winner.setSnapshot(AggregateFixture.anAssignedProcessing().toSnapshot());
            first.getTransaction().commit();
            loser.setSnapshot(AggregateFixture.pendingProcessingOf(
                    shop.fulfillment.kernel.OrderFixture.orderWithQuantity(2)).toSnapshot());

            assertThatThrownBy(second::flush).isInstanceOf(PersistenceException.class);
        } finally {
            first.close();
            second.close();
            rows.deleteById(STALE.value());
        }
    }

    private DocumentOrderProcessingRepository.OrderProcessingEntity beginAndLoad(EntityManager context) {
        context.getTransaction().begin();
        return context.find(DocumentOrderProcessingRepository.OrderProcessingEntity.class, STALE.value());
    }

    /** Writes the aggregate, then leaves it: the row is read back from the database rather than from the first-level cache. */
    private void saved(OrderFulfillmentProcess processing) {
        processings.save(processing);
        entityManager.flush();
        entityManager.clear();
    }
}
