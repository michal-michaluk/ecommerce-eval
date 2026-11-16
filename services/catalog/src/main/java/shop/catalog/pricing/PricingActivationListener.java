package shop.catalog.pricing;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;

/**
 * The RULE-9 timer. Activation is polled, not slept on: the passage of time is what this adapter listens to, so no
 * timer lives in the model — {@link ActivationDue} owns the rule, {@link PricingService} owns the transaction, and this
 * class only asks the clock and hands every offer with an unmarked entry to the primary port.
 *
 * <p>The interval is {@code catalog.pricing.activation-interval} (default {@code PT60S}); an annotation cannot take a
 * bean, so the key is read here rather than from the bound settings. One instant is taken per poll, so a poll that
 * catches up after downtime reports {@code occurredAt} = the poll and {@code effectiveFrom} = the entry's own
 * {@code validFrom} (13 RULE-8).
 */
@Component
@RequiredArgsConstructor
class PricingActivationListener {

    private final PricingService pricing;
    private final NormalizingPricingRepository awaiting;
    private final Clock clock;

    @Scheduled(fixedDelayString = "${catalog.pricing.activation-interval:PT60S}")
    void activateDueEntries() {
        Instant now = Instant.now(clock);
        awaiting.offerIdsAwaitingActivation().forEach(offerId -> pricing.activateDue(offerId, now));
    }
}
