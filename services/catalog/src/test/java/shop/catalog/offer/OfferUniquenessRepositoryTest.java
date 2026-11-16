package shop.catalog.offer;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import shop.catalog.IntegrationTest;

import static org.assertj.core.api.Assertions.assertThat;
import static shop.catalog.TestTransaction.transactional;

/**
 * The RULE-11 answer as the database holds it: an identifier is taken by another offer while that offer is not removed — whether the
 * offer carries it in its document or only in a frozen version — the asking offer never collides with itself, and removal, terminal and
 * never restarted (RULE-6), frees the identifiers for a new offer.
 */
@IntegrationTest
class OfferUniquenessRepositoryTest {

    @Autowired
    DocumentOfferRepository repository;
    @Autowired
    OfferUniquenessRepository registry;

    @Test
    void answersAnIdentifierIsTakenByAnotherLiveOffer() {
        String productNumber = OfferDocumentFixture.uniqueProductNumber();
        String eanGtin = OfferDocumentFixture.uniqueEanGtin();
        OfferEditor stored = OfferDocumentFixture.draftOffer(OfferDocumentFixture.uniqueOfferId(), productNumber, eanGtin);
        transactional(() -> repository.save(stored));

        assertThat(registry.isProductNumberTakenByOther(productNumber, OfferDocumentFixture.uniqueOfferId())).isTrue();
        assertThat(registry.isEanGtinTakenByOther(eanGtin, OfferDocumentFixture.uniqueOfferId())).isTrue();
    }

    @Test
    void answersAnIdentifierIsTakenWhileOnlyAFrozenVersionStillCarriesIt() {
        String productNumber = OfferDocumentFixture.uniqueProductNumber();
        String eanGtin = OfferDocumentFixture.uniqueEanGtin();
        OfferEditor published = OfferDocumentFixture.publishedOffer(OfferDocumentFixture.uniqueOfferId(), productNumber, eanGtin);
        String replacementNumber = OfferDocumentFixture.uniqueProductNumber();
        String replacementEanGtin = OfferDocumentFixture.uniqueEanGtin();
        published.updateDescription(ProductDescriptionDraftFixture.draftWithIdentifiers(replacementNumber, replacementEanGtin),
                OfferFixture.freeIdentifiers());
        transactional(() -> repository.save(published));

        assertThat(registry.isProductNumberTakenByOther(productNumber, OfferDocumentFixture.uniqueOfferId())).isTrue();
        assertThat(registry.isEanGtinTakenByOther(eanGtin, OfferDocumentFixture.uniqueOfferId())).isTrue();
    }

    @Test
    void answersAnIdentifierIsFreeForTheOfferThatCarriesIt() {
        String productNumber = OfferDocumentFixture.uniqueProductNumber();
        String eanGtin = OfferDocumentFixture.uniqueEanGtin();
        OfferEditor stored = OfferDocumentFixture.draftOffer(OfferDocumentFixture.uniqueOfferId(), productNumber, eanGtin);
        transactional(() -> repository.save(stored));

        assertThat(registry.isProductNumberTakenByOther(productNumber, stored.offerId)).isFalse();
        assertThat(registry.isEanGtinTakenByOther(eanGtin, stored.offerId)).isFalse();
    }

    @Test
    void answersARemovedOfferKeepsItsIdentifiersReserved() {
        String productNumber = OfferDocumentFixture.uniqueProductNumber();
        String eanGtin = OfferDocumentFixture.uniqueEanGtin();
        OfferEditor removed = OfferDocumentFixture.removedOffer(OfferDocumentFixture.uniqueOfferId(), productNumber, eanGtin);
        transactional(() -> repository.save(removed));

        assertThat(registry.isProductNumberTakenByOther(productNumber, OfferDocumentFixture.uniqueOfferId())).isTrue();
        assertThat(registry.isEanGtinTakenByOther(eanGtin, OfferDocumentFixture.uniqueOfferId())).isTrue();
    }

    @Test
    void answersAnIdentifierNoOfferCarriesIsFree() {
        assertThat(registry.isProductNumberTakenByOther(OfferDocumentFixture.uniqueProductNumber(),
                OfferDocumentFixture.uniqueOfferId())).isFalse();
        assertThat(registry.isEanGtinTakenByOther(OfferDocumentFixture.uniqueEanGtin(),
                OfferDocumentFixture.uniqueOfferId())).isFalse();
    }
}
