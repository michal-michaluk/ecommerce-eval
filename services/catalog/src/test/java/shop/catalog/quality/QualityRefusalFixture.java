package shop.catalog.quality;

/**
 * A real quality failure for {@code shop.catalog.ErrorMappingTest}: {@link QualityException} reports a configuration 08 cannot
 * interpret, which is no command refusal — the one advice must leave it to the framework instead of inventing a `reason` no client
 * can act on.
 */
public final class QualityRefusalFixture {

    private QualityRefusalFixture() {
    }

    public static RuntimeException minProseCharsNotPositive() {
        return new QualityException(QualityException.Error.MIN_PROSE_CHARS_NOT_POSITIVE, 0);
    }
}
