package shop.catalog.offer;

/**
 * The population rule a value object cannot police (01 RULE-8, 02 RULE-11): whether an identifier is already carried by a
 * <em>different</em> offer. The aggregate asks and refuses on the answer; it never queries a repository itself.
 */
interface OfferRegistry {

    boolean isProductNumberTakenByOther(String productNumber, String offerId);

    boolean isEanGtinTakenByOther(String eanGtin, String offerId);
}
