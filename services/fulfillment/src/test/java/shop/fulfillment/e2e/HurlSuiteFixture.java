package shop.fulfillment.e2e;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Runs {@code hurl --test} over the contract file and answers its exit code: one process, one report, and a non-zero answer
 * fails the Gradle task.
 *
 * <p>The child inherits this process's streams, so hurl's own per-request report and the diff of an unmatched assert land in
 * the build output where the failing request is. {@code fulfillment.e2e.suite} is the file to run, so the suite can be
 * replayed from an IDE or over a hand-written copy of one request.
 */
public final class HurlSuiteFixture {

    private static final Logger log = LoggerFactory.getLogger(HurlSuiteFixture.class);

    private static final String SUITE = "fulfillment.e2e.suite";
    private static final String DEFAULT_SUITE = "e2e/fulfillment-api.hurl";

    private HurlSuiteFixture() {
    }

    public static int run(Map<String, String> variables) {
        log.info("hurl --test over {} with the variables {}", suite(), variables.keySet());
        try {
            return new ProcessBuilder(commandOf(variables)).inheritIO().start().waitFor();
        } catch (IOException hurlUnavailable) {
            throw new IllegalStateException("cannot run hurl — install it (brew install hurl) and run again", hurlUnavailable);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while the contract of element 11 ran", interrupted);
        }
    }

    private static List<String> commandOf(Map<String, String> variables) {
        List<String> command = new ArrayList<>(List.of("hurl", "--test", "--max-time", "30"));
        variables.forEach((name, value) -> command.addAll(List.of("--variable", name + "=" + value)));
        command.add(suite());
        return command;
    }

    private static String suite() {
        return System.getProperty(SUITE, DEFAULT_SUITE);
    }
}
