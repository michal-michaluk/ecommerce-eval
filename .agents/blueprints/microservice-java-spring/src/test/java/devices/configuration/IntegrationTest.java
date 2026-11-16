package devices.configuration;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.annotation.AliasFor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.transaction.annotation.Transactional;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Everything an integration test needs: the application context on a random port, the {@code integration-test}
 * profile that starts the Testcontainers PostgreSQL, a transaction rolled back after each test, and the
 * application events recorded for the test to assert.
 *
 * <p>It is one annotation because a test lives in the package of the class it tests, and a bounded context's
 * model type may not depend on Spring: a raw {@code @Transactional} or {@code @RecordApplicationEvents} in that
 * package would be a model dependency on a framework.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles
@Transactional
@RecordApplicationEvents
public @interface IntegrationTest {

    @AliasFor(annotation = ActiveProfiles.class, attribute = "profiles")
    String[] profiles() default {"integration-test"};
}
