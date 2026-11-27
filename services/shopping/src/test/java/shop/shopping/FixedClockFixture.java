package shop.shopping;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

/**
 * The instant every contract test reads: {@code 2026-11-26T10:00:00Z}, the day {@code prototypes/basket-api.prove.md} is
 * written around.
 *
 * <p>It is the clock the service counts from, so the two things the payload otherwise takes from the wall become literals a test
 * can write down: the delivery estimate, which is element 05's {@code today + 2 … today + 4} calendar days in
 * {@code Europe/Warsaw}, answers <strong>{@code 2026-11-28 … 2026-11-30}</strong> here and in every body asserted against it, and
 * a handover's {@code createdAt} is one value.
 *
 * <p>Those are not the scenario's {@code est_from}/{@code est_to} defaults — the document's {@code **vars:**} block says
 * {@code 2026-11-28} and {@code 2026-12-02}, which is a {@code +2 … +6} pair and not the range element 05 fixes. The block is
 * overridden by the runner ({@code --var}, today {@code +2}/{@code +4}), so the scenario still matches the service; the literal
 * defaults are a defect of the document, reported rather than copied here.
 */
@TestConfiguration
public class FixedClockFixture {

    public static final Instant NOW = Instant.parse("2026-11-26T10:00:00Z");

    @Bean
    @Primary
    Clock fixedClock() {
        return Clock.fixed(NOW, ZoneOffset.UTC);
    }
}
