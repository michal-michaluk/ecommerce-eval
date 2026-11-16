package shop.catalog.pricing;

/**
 * The one local fact this context keeps about an offer besides its prices (17 RULE-4): whether the identity exists at all.
 *
 * <p>It is a port rather than the repository behind it, because 12 answers an unknown offer with `404` and the pricing model cannot say
 * that — {@link PricingService} opens a model for any identity it is given. The fact arrives through
 * {@link KnownOfferListener} from the offer snapshot, so no command asks the offer context anything.
 */
interface KnownOffer {

    boolean knows(String offerId);
}
