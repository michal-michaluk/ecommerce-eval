package shop.catalog.e2e;

import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.TopicDescription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.apache.kafka.clients.admin.AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG;
import static org.awaitility.Awaitility.await;

/**
 * Waits until the events topic of 13 has a partition leader, which is the one thing the suite needs before it publishes anything.
 *
 * <p>13 sends each event inside the producing transaction and waits for the broker's acknowledgement
 * ({@code integration.synchronous-in-transaction}), so a topic whose leader is not elected yet makes the first publication wait out the
 * producer's {@code max.block.ms} and the request carrying it hit the client's timeout — a {@code PATCH} that leaves no answer at all.
 * {@code IntegrationConfiguration} creates the topic while the context starts; this waits for the broker to finish electing, and stops
 * with the one action that helps instead of hanging on if it never does.
 *
 * <p>It neither starts nor owns a container: the broker it asks is the one the application was pointed at, read from the same system
 * property the application reads, so the harness can never check a broker the service does not use.
 */
public final class CatalogTopicFixture {

    private static final Logger log = LoggerFactory.getLogger(CatalogTopicFixture.class);

    private static final String BROKERS = "spring.kafka.bootstrap-servers";
    private static final String TOPIC = "catalog.events.topic";
    private static final String DEFAULT_TOPIC = "catalog.offer.events.v1";
    private static final Duration TIMEOUT = Duration.ofSeconds(60);
    private static final Duration POLL = Duration.ofMillis(250);
    private static final Duration ADMIN_CALL = Duration.ofSeconds(15);

    private CatalogTopicFixture() {
    }

    public static void awaitLeader() {
        try (Admin admin = Admin.create(Map.of(BOOTSTRAP_SERVERS_CONFIG, brokers()))) {
            String topic = topic();
            try {
                await().atMost(TIMEOUT).pollInterval(POLL).until(() -> hasLeader(admin, topic));
            } catch (org.awaitility.core.ConditionTimeoutException neverElected) {
                throw new IllegalStateException("topic " + topic + " on " + brokers() + " has no partition leader after "
                        + TIMEOUT + ": every publication of 13 would block inside its transaction. Check the broker, then run again",
                        neverElected);
            }
            log.info("topic {} has a partition leader", topic);
        }
    }

    private static boolean hasLeader(Admin admin, String topic) {
        try {
            TopicDescription described = admin.describeTopics(List.of(topic)).allTopicNames()
                    .get(ADMIN_CALL.toSeconds(), TimeUnit.SECONDS).get(topic);
            return described.partitions().stream().allMatch(partition -> partition.leader() != null);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while the harness waited for " + topic, interrupted);
        } catch (ExecutionException | TimeoutException notYet) {
            return false;
        }
    }

    private static String brokers() {
        return Objects.requireNonNull(System.getProperty(BROKERS), BROKERS + " was never set");
    }

    private static String topic() {
        return System.getProperty(TOPIC, DEFAULT_TOPIC);
    }
}
