package shop.catalog.reads;

import java.time.Instant;
import java.util.Optional;

/**
 * The contract port that asks 10 what the offer costs on a date the reader picks, so the pricing view of 16 can show the effect of
 * stacking before anything is saved (RULE-4).
 *
 * <p>The question is the pricing view's own: currency and instant in, the amount in minor units out — a {@link Result}, not the
 * pricing context's snapshot, whose components it never names. The answer is asked on every render because the date is the reader's
 * input; the projected price series answers the rest of the screen without a hop.
 *
 * <p>{@code Optional} is the whole "no open price" vocabulary: an offer without a price entry for that currency has no preview, and
 * the amount {@code 0} is a real price and never a placeholder (10, 13 RULE-7).
 */
public interface EffectivePrices {

    Optional<Result> effectivePriceOf(String offerId, String currency, Instant at);

    /** What the reader pays in {@code currency} at {@code at}: 10's amount, in minor units (D1). */
    record Result(int amountMinor, String currency) {

    }
}
