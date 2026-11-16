package shop.catalog;

import org.mockito.Mockito;
import org.springframework.context.event.EventListener;
import shop.catalog.tools.EventEnvelope;
import shop.catalog.tools.EventStream;

import java.util.Arrays;
import java.util.List;

/** What an adapter actually shipped: the envelopes it handed to the stream, and the facts it is wired to listen to. */
public class PublishedEvents {

    public static List<EventEnvelope> of(EventStream stream) {
        return Mockito.mockingDetails(stream).getInvocations().stream()
                .filter(invocation -> invocation.getMethod().getName().equals("publish"))
                .map(invocation -> (EventEnvelope) invocation.getArgument(0))
                .toList();
    }

    public static EventEnvelope only(List<EventEnvelope> published) {
        if (published.size() != 1) {
            throw new AssertionError("expected exactly one published envelope, got " + published);
        }
        return published.getFirst();
    }

    /** The facts an integration adapter listens to — the executable form of "nothing else ever leaves the process" (13 RULE-1). */
    public static List<Class<?>> factsHandledBy(Class<?> integration) {
        return Arrays.stream(integration.getDeclaredMethods())
                .filter(method -> method.isAnnotationPresent(EventListener.class))
                .map(method -> method.getParameterTypes()[0])
                .toList();
    }
}
