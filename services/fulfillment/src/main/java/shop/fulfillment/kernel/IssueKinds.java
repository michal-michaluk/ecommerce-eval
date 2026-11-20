package shop.fulfillment.kernel;

/**
 * The issue kinds the transcript names. The set is deliberately open — "whatever" is a source word too — so an issue
 * carries a free-form code stored verbatim, and these four constants only spare the known ones a typo.
 *
 * <p>Not an enum on purpose: closing the set would reject the kinds nobody has named yet, and {@code DAMAGED} is not a
 * member at all — the word exists only in a superseded document.
 */
public final class IssueKinds {

    /** The units are gone: "they are lost". */
    public static final String LOST = "LOST";

    /** The units no longer exist as goods. */
    public static final String DESTROYED = "DESTROYED";

    /** The units were taken. */
    public static final String STOLEN = "STOLEN";

    /** The units exist but are not where the warehouse system says. */
    public static final String WRONG_LOCATION = "WRONG_LOCATION";

    private IssueKinds() {
    }
}
