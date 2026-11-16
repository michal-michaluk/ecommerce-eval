package shop.catalog.review;

/**
 * A real review refusal for {@code shop.catalog.ErrorMappingTest}: {@link ReviewException} is package-private, so the one advice that
 * maps every context's refusal to a status can only be driven from inside this package.
 */
public final class ReviewRefusalFixture {

    private ReviewRefusalFixture() {
    }

    public static RuntimeException reviewNotOpen() {
        return new ReviewException(ReviewException.Error.REVIEW_NOT_OPEN, "REV-1A2B3C4D", "decided");
    }
}
