package shop.shopping.kernel;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Element 05's answer: an inclusive day range and where it came from — or the named non-answer {@code unknown}.
 *
 * <p>Two enums carry the shape the specification fixes: {@link Kind} says whether there is a range to render, {@link Source}
 * says which input produced the answer. Only {@link Kind#estimate} is renderable — an {@code unknown} hides the delivery line
 * rather than printing a guess (Q22, D24), so {@code unknown} is a named value and never {@code null} (element 05
 * §Recommendations 1).
 *
 * <p>{@code from} and {@code to} are calendar days of {@code basket.estimate.zone}, inclusive at both ends (D43, Q21/D36), and
 * they are present exactly when {@code kind} is {@link Kind#estimate}. An {@code unknown} carries no range at all: a range
 * beside "there is nothing to say" is a number a reader could trust by mistake. The pairing is checked once, here, so no caller
 * repeats it — it is a programming error, because an {@code Estimate} is built by element 05's policy and never by a client,
 * and it reaches the caller the way an absent component does: a JDK exception, {@link IllegalArgumentException} for the
 * mispairing and {@link NullPointerException} for a missing component (the split {@link Money} states — a client-supplied value
 * refuses with {@link ShoppingException}, a programming error with a JDK type).
 *
 * <p>The estimate is <em>when</em> and never <em>how much</em>: it carries no {@link Money}, and no line of element 04 reads
 * it, because {@code Dostawa od 9,99 zł} comes from the delivery rate (element 05 §Recommendations 2). It is also not a promise
 * — the deferred algorithm (issue #71) may replace the range; what it must keep is this shape.
 *
 * <p>It lives in {@code kernel/} because it is part of the handover payload: the basket composes it, the checkout slice reads it,
 * and no slice type may cross (element 09 §Recommendations 1, {@code structure.kernel-public}).
 *
 * @param kind   whether there is a range to render
 * @param from   the first day of the inclusive range, absent when {@code kind} is {@link Kind#unknown}
 * @param to     the last day of the inclusive range, absent when {@code kind} is {@link Kind#unknown}
 * @param source which input produced the answer
 */
public record Estimate(Kind kind, LocalDate from, LocalDate to, Source source) {

    /** Whether there is a range to render — element 05 §Interface, {@code kind: estimate | unknown}. */
    public enum Kind {
        estimate,
        unknown
    }

    /**
     * Which input produced the answer — element 05 §Interface, {@code source: configured | observed}.
     *
     * <p>{@code configured} is a rule of the {@code basket.estimate.*} block and {@code observed} a range derived from what the
     * offers and carriers reported; the second has no rule yet, so the deferred algorithm (issue #71) is the only future author
     * of it. Every answer of this increment is {@code configured}, because this increment has no observation to derive
     * anything from.
     *
     * <p>That is exact for the range (D36) and for the fallback I5 produces — {@code basket.estimate.fallback} is itself
     * configuration — and it is a compromise for the {@code unknown} of I4 and I7, where nothing selected or every row blocked
     * left nothing to estimate and no rule ran at all. The enum has no third constant to name that with, and inventing one would
     * be a specification change rather than an implementation choice (D7), so the caller must not read {@code source} on an
     * {@code unknown} as evidence that a rule computed something: on a non-answer it records only which side of the interface
     * the non-answer came from, and a renderer hides the line either way (Q22).
     */
    public enum Source {
        configured,
        observed
    }

    public Estimate {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(source, "source");
        if (kind == Kind.estimate) {
            ensureOrderedRange(from, to);
        } else {
            ensureNoRange(from, to);
        }
    }

    /**
     * The range a configured rule states.
     *
     * @param from   the first day, inclusive
     * @param to     the last day, inclusive, never before {@code from}
     * @param source whether a rule or an observation produced it
     * @return the renderable estimate
     */
    public static Estimate estimatedBetween(LocalDate from, LocalDate to, Source source) {
        return new Estimate(Kind.estimate, from, to, source);
    }

    /**
     * The answer that renders nothing — nothing to estimate, or no configuration to estimate with (I4, I5, I7).
     *
     * @param source which input produced the non-answer; this increment always answers {@link Source#configured}
     * @return the named non-answer, never {@code null}
     */
    public static Estimate unknown(Source source) {
        return new Estimate(Kind.unknown, null, null, source);
    }

    /** Whether a range is renderable — the one question a caller asks before printing a delivery line (Q22, D24). */
    public boolean isARange() {
        return kind == Kind.estimate;
    }

    private static void ensureOrderedRange(LocalDate from, LocalDate to) {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        if (from.isAfter(to)) {
            throw new IllegalArgumentException("an estimate's range is ordered, got " + from + " … " + to);
        }
    }

    private static void ensureNoRange(LocalDate from, LocalDate to) {
        if (from != null || to != null) {
            throw new IllegalArgumentException("an unknown estimate carries no range, got " + from + " … " + to);
        }
    }
}
