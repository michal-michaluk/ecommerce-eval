package shop.catalog.offer;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * The {@link OfferRegistry} adapter: answers RULE-11 across every identifier an offer holds, and the schema answers the live half of
 * it again.
 *
 * <p>The predicate, in one line: <em>an identifier is taken by another offer whenever that offer carries it, in its document row or in
 * any frozen version, whatever that offer's state is</em>. Both halves matter: the document row holds what the draft carries now, while
 * a frozen version holds what was published — and a draft may replace an identifier ({@link ProductDescription.Draft#merge} never
 * clears a component), so an identifier kept only in a version would otherwise become claimable by a second offer while the first
 * still publishes it (RULE-4, RULE-11).
 *
 * <p>Identifiers are reserved **forever**, removal included (D43). Removal is terminal and never restarts (RULE-6, Q11); were a
 * removed offer's identifiers claimable, a second offer could carry the same `productNumber`/`eanGtin` and "the same product is never
 * offered again" would be bypassable by creating a new offer. The price of this reading is that a genuinely new product cannot reuse
 * a retired GTIN — the correct outcome for a GTIN, which identifies a product and not a listing.
 *
 * <p>The unique indexes of {@code offer_document} ({@code 0003-offer-document}) restate the document half of the predicate, so the
 * answer this adapter gives and the answer the database enforces cannot drift apart. The frozen half has no constraint to back it — a
 * cross-table rule is not expressible on {@code offer_version} alone — so it rests on this port, which every draft edit and every
 * freeze asks before the write.
 */
@Repository
class OfferUniquenessRepository implements OfferRegistry {

    private static final String PRODUCT_NUMBER_TAKEN = """
            select exists(
                select 1 from offer_document live
                where live.offer_id <> :offerId and live.product_number = :identifier
                union all
                select 1 from offer_version frozen
                    join offer_document live on live.offer_id = frozen.offer_id
                where live.offer_id <> :offerId
                    and frozen.description #>> '{identifiers,productNumber}' = :identifier)
            """;

    private static final String EAN_GTIN_TAKEN = """
            select exists(
                select 1 from offer_document live
                where live.offer_id <> :offerId and live.ean_gtin = :identifier
                union all
                select 1 from offer_version frozen
                    join offer_document live on live.offer_id = frozen.offer_id
                where live.offer_id <> :offerId
                    and frozen.description #>> '{identifiers,eanGtin}' = :identifier)
            """;

    private final NamedParameterJdbcTemplate templates;

    OfferUniquenessRepository(NamedParameterJdbcTemplate templates) {
        this.templates = templates;
    }

    @Override
    public boolean isProductNumberTakenByOther(String productNumber, String offerId) {
        return isTaken(PRODUCT_NUMBER_TAKEN, productNumber, offerId);
    }

    @Override
    public boolean isEanGtinTakenByOther(String eanGtin, String offerId) {
        return isTaken(EAN_GTIN_TAKEN, eanGtin, offerId);
    }

    private boolean isTaken(String sql, String identifier, String offerId) {
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("identifier", identifier)
                .addValue("offerId", offerId);
        return Boolean.TRUE.equals(templates.queryForObject(sql, parameters, Boolean.class));
    }
}
