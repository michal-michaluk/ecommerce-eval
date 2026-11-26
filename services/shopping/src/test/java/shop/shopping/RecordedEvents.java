package shop.shopping;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.test.context.event.ApplicationEvents;

import java.util.List;

/**
 * The application events the current test recorded, read in the test's own vocabulary:
 * {@code emitted.of(<DomainEvent>.class)}.
 *
 * <p>It exists so a test in a bounded-context package never names {@code ApplicationEvents} itself — that type is Spring,
 * and the context packages are governed by the model rules. Spring registers {@code ApplicationEvents} as a dependency of
 * the test instance, after the context is refreshed, so the provider defers the lookup until a test method asks.
 */
@Profile("integration-test")
@Component
public class RecordedEvents {

    private final ObjectProvider<ApplicationEvents> events;

    RecordedEvents(ObjectProvider<ApplicationEvents> events) {
        this.events = events;
    }

    public <T> List<T> of(Class<T> type) {
        return events.getObject().stream(type).toList();
    }
}
