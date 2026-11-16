package shop.catalog;

import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.errors.TopicExistsException;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

@Profile("integration-test")
@Configuration
class IntegrationConfiguration {

    private static final String EVENTS_TOPIC = System.getProperty("catalog.events.topic", "catalog.offer.events.v1");

    static {
        var kafkaContainer = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:5.4.3"))
                .withReuse(true)
                .withNetwork(null);
        kafkaContainer.start();
        System.setProperty("spring.kafka.bootstrap-servers", kafkaContainer.getBootstrapServers());
        createEventsTopic(kafkaContainer.getBootstrapServers());

        var postgreSQLContainer = new PostgreSQLContainer<>(DockerImageName.parse("postgres:15.3-alpine"))
                .withReuse(true);
        postgreSQLContainer.start();
        System.setProperty("spring.datasource.url", postgreSQLContainer.getJdbcUrl());
        System.setProperty("spring.datasource.username", postgreSQLContainer.getUsername());
        System.setProperty("spring.datasource.password", postgreSQLContainer.getPassword());
    }

    /**
     * The topic D28 names, created once with the broker: a producer without it cannot ship the first record.
     *
     * <p>One partition and one replica, hosted by the broker the cluster actually has. Naming the replica set by a literal broker id —
     * {@code Map.of(0, List.of(0))}, partition 0 on broker 0 — named a broker this container never is: it runs with
     * {@code KAFKA_BROKER_ID=1}, so the topic was accepted with an assignment that can never produce a leader, {@code createTopics}
     * answered only after its request timeout, and the first publication then waited out the producer's {@code max.block.ms} inside
     * its transaction. A machine that already carried a topic an earlier producer auto-created never noticed; a clean one failed.
     */
    private static void createEventsTopic(String bootstrapServers) {
        try (Admin admin = Admin.create(Map.of(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers))) {
            admin.createTopics(List.of(new NewTopic(EVENTS_TOPIC, Map.of(0, List.of(soleBrokerId(admin)))))).all().get();
        } catch (ExecutionException failed) {
            ensureTopicAlreadyExisted(failed);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while creating " + EVENTS_TOPIC, interrupted);
        }
    }

    /** The one broker this container runs, asked of the cluster so no replica set names an id that does not exist. */
    private static int soleBrokerId(Admin admin) throws ExecutionException, InterruptedException {
        return admin.describeCluster().nodes().get().iterator().next().id();
    }

    private static void ensureTopicAlreadyExisted(ExecutionException failed) {
        boolean existing = failed.getCause() instanceof TopicExistsException;
        if (!existing) {
            throw new IllegalStateException("cannot create " + EVENTS_TOPIC, failed.getCause());
        }
    }
}
