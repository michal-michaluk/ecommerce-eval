package shop.shopping.basket;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;

import shop.shopping.basket.BasketConfiguration.Settings.EstimateSettings;
import shop.shopping.basket.BasketConfiguration.Settings.EstimateSettings.DefaultDays;
import shop.shopping.kernel.Estimate;

/**
 * Element 05: when the customer gets what is selected, as an <strong>interface with a hardcoded default</strong> — the day
 * range is decided, the algorithm is not (D16, D36).
 *
 * <p>{@code from = today + 2 days}, {@code to = today + 4 days}, both inclusive (D36). No formula exists in any material — a
 * 71.2 s stretch of the capture was never transcribed (F5, R1) — so none is invented here (D7): the constant is what issue #71
 * replaces, and this type is the shape it must keep.
 *
 * <h2>Time</h2>
 * The policy never reads a clock: {@code now} arrives in the {@link Request} (element 05 §Interface; {@code clock.injected},
 * {@code policy.no-io}). The caller takes it from the service's request-scoped {@link java.time.Clock}, never from a cached
 * one, because a basket left open overnight must not keep showing yesterday's range (element 05 §Recommendations 4). The
 * <em>day</em> is the customer's calendar day in {@code basket.estimate.zone} (D43): {@code 23:30} in {@code Europe/Warsaw} is
 * still today even though UTC has already moved on, and {@code 00:30} there is already tomorrow though UTC says yesterday. A
 * range counted from any other day would arrive one day early for half the evening.
 *
 * <h2>Granularity</h2>
 * One {@link Estimate} answers <strong>one seller shipment</strong> and the basket shows the latest {@code to} across shipments
 * (element 05 §Interface). Both halves of that rule sit with the caller, and deliberately: this type answers one shipment, so a
 * multi-seller selection is grouped by {@link BasketItem#seller()} before the call, and the figure the basket publishes is the
 * latest {@code to} of the shipments that answered {@link Estimate.Kind#estimate} — an {@code unknown} shipment hides its own
 * line and contributes no date (Q22). Neither half can be enforced here: a mixed-seller list would have to be refused with an
 * exception, which a policy does not do ({@code policy.no-exceptions}), and comparing {@code to} across calls is what
 * {@link ShipmentEstimates} holds. Today grouping answers the same days for every shipment, because the range depends on
 * {@code now} and the zone only — a whole-basket call answers what the grouped calls would
 * ({@code mixedSellersInOneRequestAnswerTheSameDaysAsOneShipment}) — and the rule is still stated per shipment because the
 * deferred algorithm (issue #71) will be able to make ranges differ, and a caller that never grouped would then have to be
 * rewritten.
 *
 * <h2>What is estimated</h2>
 * Rows reach this policy as the aggregate stores them, with their own {@code selected} flag — there is no second parameter for
 * the selection, for the reason element 04 states: a second source of truth could disagree with the aggregate (RULE-9,
 * RULE-10). The policy filters them here, so I4 is answered by this type and not by every caller remembering to pre-filter. A
 * row contributes only when it is selected <em>and</em> handable over: {@code outOfStock} and {@code unavailable} rows
 * contribute nothing (I2, I3), because such a row blocks the handover instead of lengthening the delivery (D33), while
 * {@code stale} on its own never blocks and never removes a row. Nothing selected is {@code unknown} (I4) and so is a
 * selection whose every row is blocked (I7): nothing is handable over, so there is nothing to estimate.
 *
 * <h2>Configuration, and never an exception</h2>
 * The three keys {@code basket.estimate.default-days}, {@code basket.estimate.zone} and {@code basket.estimate.fallback}
 * bind into {@link EstimateSettings}. No {@code @DefaultValue} sits on that record: a key the deployment did not state stays
 * <em>absent</em> and is observable here, which is what makes I5 answerable. An absent block, an absent key, a zone
 * {@link ZoneId} does not know or a range whose {@code from} is after its {@code to} all answer
 * {@link Estimate#unknown} — hidden, never a guess, never an exception (I5, Q22). The shipped {@code application.yml} states
 * every key behind a placeholder ({@code ${SHOPPING_BASKET_ESTIMATE_FROM:2}}), so a deployment always has a value and I5's
 * {@code unknown} is reachable only when the whole block is absent — which is the case a unit test holds. {@link #defaultEstimate()}
 * is the other half of D7: the constant {@code {from: 2, to: 4}} / {@code Europe/Warsaw} / {@code unknown} this element fixes,
 * stated once in code so a runner or a test can install it without a deployment.
 *
 * <h2>What it is not</h2>
 * The channel arrives in the request and changes nothing (I6): SMART changes the <em>price</em> of delivery, not the day range
 * (element 04 S6), and it is carried so the deferred algorithm can read it without changing this interface. The estimate never
 * answers a {@link shop.shopping.kernel.Money} and no amount is ever computed from it (element 05 §Recommendations 2).
 *
 * @param settings the {@code basket.estimate.*} block this policy reads; {@code null} when the deployment states none, which
 *                 answers {@link Estimate#unknown} rather than an exception
 * @see Estimate
 */
record BasketDeliveryEstimate(EstimateSettings settings) {

    private static final int FROM_DAYS = 2;

    private static final int TO_DAYS = 4;

    private static final String ZONE = "Europe/Warsaw";

    private static final String UNKNOWN = "unknown";

    /**
     * The constant element 05 fixes (D36, D43): {@code today + 2 … today + 4} calendar days in {@code Europe/Warsaw}.
     *
     * <p>The explicit default, stated once, next to the policy rather than in a deployment file ({@code policy.explicit-default}).
     * Element 05 §Configuration and I5 read the absent case two ways — the keys carry those defaults, and an absent key answers
     * the fallback — and they can only differ in who supplies the number: I5 decides the absent case, because it is the row a
     * test can hold, so a deployment that states nothing gets {@link Estimate#unknown} until someone calls this factory or
     * configures the block.
     */
    static BasketDeliveryEstimate defaultEstimate() {
        return new BasketDeliveryEstimate(
                new EstimateSettings(new DefaultDays(FROM_DAYS, TO_DAYS), ZONE, UNKNOWN));
    }

    /**
     * The estimate of one seller shipment.
     *
     * @param request the shipment's rows as the basket stores them, the channel and the instant to count from
     * @return the shipment's range, or the named non-answer when there is nothing to estimate or nothing to estimate with
     */
    Estimate estimate(Request request) {
        Objects.requireNonNull(request, "request");
        return hasHandableRow(request) && configured() ? estimated(request.now()) : unknown();
    }

    private static boolean hasHandableRow(Request request) {
        return request.items().stream()
                .anyMatch(item -> item.selected() && !item.availability().blocksHandover());
    }

    /**
     * Whether the block can produce a range at all — every key present, ordered, a zone {@link ZoneId} knows, and the one
     * fallback value element 05 defines.
     *
     * <p>Anything else answers {@link Estimate#unknown} (I5, Q22): a half-configured estimate hides rather than guesses, and
     * {@link #defaultEstimate()} is what a deployment states instead of trusting a silent default — which is also why no
     * {@code @DefaultValue} fills an absent key in.
     */
    private boolean configured() {
        return settings != null
                && settings.defaultDays() != null
                && settings.zone() != null
                && ordered()
                && UNKNOWN.equals(settings.fallback())
                && zone() != null;
    }

    private boolean ordered() {
        return settings.defaultDays().from() <= settings.defaultDays().to();
    }

    private Estimate estimated(Instant now) {
        LocalDate today = LocalDate.ofInstant(now, zone());
        DefaultDays days = settings.defaultDays();
        return Estimate.estimatedBetween(today.plusDays(days.from()), today.plusDays(days.to()),
                Estimate.Source.configured);
    }

    /**
     * The configured zone, or {@code null} when {@link ZoneId} does not know the identifier — the configuration answers a
     * value here and is never an exception ({@code policy.no-exceptions}).
     */
    private ZoneId zone() {
        try {
            return ZoneId.of(settings.zone());
        } catch (DateTimeException notAZone) {
            return null;
        }
    }

    private static Estimate unknown() {
        return Estimate.unknown(Estimate.Source.configured);
    }

    /**
     * Element 05's {@code request}: one seller shipment's rows, the channel, and the instant to count the range from.
     *
     * <p>{@code items} are {@link BasketItem}s rather than a projection, because the aggregate already owns that record and a
     * second shape would be a second place selection and availability could disagree. They arrive as the aggregate stores them,
     * selected or not: the row's own flag is the selection ({@code BasketDeliveryEstimate} filters it), so a caller never
     * re-implements I4 to know what to pass. A caller that publishes per-shipment lines groups by {@link BasketItem#seller()}
     * and takes the latest {@code to}; a whole-basket call is not refused, and today it answers exactly what the grouped calls
     * do. {@code quantity} is carried although the hardcoded range ignores it: the interface is fixed now so issue #71 can
     * count units without moving the caller. The channel is carried for the same reason and for none today (I6).
     *
     * @param items   the shipment's rows, selected or not — the row's own flag is the selection
     * @param channel how the customer arrives; SMART changes the price, never the range (element 04 S6)
     * @param now     the instant the caller's request-scoped clock reports
     */
    record Request(List<BasketItem> items, Channel channel, Instant now) {

        Request {
            items = List.copyOf(items);
            Objects.requireNonNull(channel, "channel");
            Objects.requireNonNull(now, "now");
        }
    }
}
