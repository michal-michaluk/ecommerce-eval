package shop.catalog.offer;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * The offer context's primary port: every use case of 02 is one load, one command on {@link OfferEditor} and one save, and nothing
 * else. The invariants live in the aggregate, the storage shape and the publication in {@link OfferRepository}, and this class holds
 * neither (ports.md, {@code port.primary-thin}).
 *
 * <p>It reaches two in-context ports: the repository that stores the aggregate, and {@link OfferRegistry} — the population question
 * the aggregate itself asks (RULE-11) and therefore the one collaborator every command has to be handed. It publishes nothing: the
 * adapter publishes after it has written ({@code integration.publish-in-adapter}).
 *
 * <p>{@code Optional} is the whole "not found" vocabulary: a command on an unknown offer answers with {@code Optional.empty()} and
 * invents no second one, and drafting is the only call that opens a new offer (RULE-1's lifecycle starts at {@code draft}). An
 * identity is opened once — removal is terminal and version numbering never restarts (RULE-6, Q11) — so drafting an {@code offerId}
 * that already exists is refused instead of overwriting the document of an offer that may already be published.
 *
 * <p>{@link #updateMedia} names {@link Media}, a value of this context like {@link ProductDescription}: a version embeds it by value
 * and a mediator that hands a reviewed candidate over maps into it.
 */
@Service
@Transactional
public class OfferService {

    private static final String ID_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final int ID_LENGTH = 8;
    private static final int ID_ATTEMPTS = 3;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final OfferRepository offers;
    private final OfferRegistry registry;

    OfferService(OfferRepository offers, OfferRegistry registry) {
        this.offers = offers;
        this.registry = registry;
    }

    @Transactional(readOnly = true)
    public Optional<Offer> getOffer(String offerId) {
        return offers.get(offerId).map(OfferEditor::toOffer);
    }

    /** One page of the offers 12 lists: the same snapshot a write answers, because element 17 gives no read model to this resource. */
    @Transactional(readOnly = true)
    public List<Offer> offers(int page, int size) {
        return offers.findAll(page, size).stream().map(OfferEditor::toOffer).toList();
    }

    /** How many offers the list spans, so a page can state what it is a page of (D16). */
    @Transactional(readOnly = true)
    public long countOffers() {
        return offers.count();
    }

    /**
     * The frozen versions of one offer in the order they were published — the publish history of 12 (D31). Empty for an offer
     * nobody ever drafted, so the caller can answer `404` without a second load.
     */
    @Transactional(readOnly = true)
    public Optional<List<OfferVersion>> versionsOf(String offerId) {
        return offers.get(offerId).map(OfferEditor::frozenVersions);
    }

    /**
     * Opens the draft of a new offer under an identity this context mints itself (D31): {@code OFR-} plus eight uppercase
     * alphanumerics, never a client value, and a candidate another offer already holds is retried before the command refuses.
     */
    public Offer draftOffer(ProductDescription.Draft description) {
        return draftOffer(freeOfferId(), description);
    }

    public Offer draftOffer(String offerId, ProductDescription.Draft description) {
        ensureNoOfferExists(offerId);
        OfferEditor offer = OfferEditor.draftAnOffer(offerId, description, registry);
        offers.save(offer);
        return offer.toOffer();
    }

    /**
     * The whole of 12's single draft edit: the components the caller sent move together, in the one transaction this method opens, so
     * a refused media component cannot leave the description it travelled with already written (D13, D18). A {@code null} component is
     * one the request did not carry and keeps its value.
     */
    public Optional<Offer> updateDraft(String offerId, ProductDescription.Draft change, List<Media> addedMedia,
                                       LocalDate availabilityStart) {
        return apply(offerId, offer -> {
            if (change != null) {
                offer.updateDescription(change, registry);
            }
            if (addedMedia != null) {
                offer.addMedia(addedMedia);
            }
            if (availabilityStart != null) {
                offer.scheduleAvailability(availabilityStart);
            }
        });
    }

    public Optional<Offer> updateDescription(String offerId, ProductDescription.Draft change) {
        return apply(offerId, offer -> offer.updateDescription(change, registry));
    }

    public Optional<Offer> updateMedia(String offerId, List<Media> media) {
        return apply(offerId, offer -> offer.updateMedia(media));
    }

    public Optional<Offer> scheduleAvailability(String offerId, LocalDate availabilityStart) {
        return apply(offerId, offer -> offer.scheduleAvailability(availabilityStart));
    }

    public Optional<Offer> requestReview(String offerId) {
        return apply(offerId, OfferEditor::requestReview);
    }

    /**
     * Ends the offer's review because 04 rejected or withdrew the attempt — the second half of {@link #requestReview}, and the
     * only caller is the {@code OfferReview} mediator.
     *
     * <p>A command, like {@link #publishCandidate}: a decision is being recorded, so an unknown offer is this context's failure
     * instead of an empty answer nobody would read.
     */
    public Offer closeReview(String offerId, String reviewId) {
        return apply(offerId, offer -> offer.closeReview(reviewId))
                .orElseThrow(() -> new OfferException(OfferException.Error.OFFER_UNKNOWN, offerId));
    }

    public Optional<Offer> publish(String offerId, Instant publishedAt, String publishedBy) {
        return apply(offerId, offer -> offer.publish(publishedAt, publishedBy, registry));
    }

    /**
     * The draft as it stands and the state 09 decides on, read in one load — the answer of the {@code OfferDraftSnapshot} port its
     * mediator implements (D38, 09 R6/R7). The content leaves the context as values: a candidate carries a frozen description, never a
     * draft (01 RULE-4), and an offer that cannot produce one refuses here with this context's own failure.
     */
    @Transactional(readOnly = true)
    public Optional<OfferDraft> draftOf(String offerId) {
        return offers.get(offerId).map(offer -> new OfferDraft(offer.toOffer().state(), offer.toCandidate()));
    }

    /**
     * Freezes the content 04 accepted as the next version — the candidate, never the live draft (RULE-4, D38) — additive to
     * {@link #publish}, which freezes what the draft holds now. Its only caller is the {@code OfferPublication} mediator.
     *
     * <p>A command, so an unknown offer is a failure instead of an empty answer: an acceptance cannot be recorded without a version,
     * and the refusal belongs to this context.
     */
    public Offer publishCandidate(String offerId, OfferCandidate candidate, Instant publishedAt, String publishedBy) {
        return apply(offerId, offer -> offer.publishCandidate(candidate, publishedAt, publishedBy, registry))
                .orElseThrow(() -> new OfferException(OfferException.Error.OFFER_UNKNOWN, offerId));
    }

    public Optional<Offer> revert(String offerId, int sourceVersion, Instant publishedAt, String publishedBy) {
        return apply(offerId, offer -> offer.revert(sourceVersion, publishedAt, publishedBy, registry));
    }

    public Optional<Offer> removeOffer(String offerId) {
        return apply(offerId, OfferEditor::remove);
    }

    private Optional<Offer> apply(String offerId, Consumer<OfferEditor> command) {
        Optional<OfferEditor> loaded = offers.get(offerId);
        loaded.ifPresent(offer -> {
            command.accept(offer);
            offers.save(offer);
        });
        return loaded.map(OfferEditor::toOffer);
    }

    /** Opening the draft is where an offer identity comes into being, so an existing one is never re-opened (RULE-6, Q11). */
    private void ensureNoOfferExists(String offerId) {
        if (offers.get(offerId).isPresent()) {
            throw new OfferException(OfferException.Error.OFFER_ALREADY_DRAFTED, offerId);
        }
    }

    /** A minted identity another offer already holds is drawn again; after the last attempt the command's own refusal answers. */
    private String freeOfferId() {
        String offerId = mintedOfferId();
        for (int attempt = 1; attempt < ID_ATTEMPTS && offers.get(offerId).isPresent(); attempt++) {
            offerId = mintedOfferId();
        }
        return offerId;
    }

    private static String mintedOfferId() {
        StringBuilder offerId = new StringBuilder("OFR-");
        for (int position = 0; position < ID_LENGTH; position++) {
            offerId.append(ID_ALPHABET.charAt(RANDOM.nextInt(ID_ALPHABET.length())));
        }
        return offerId.toString();
    }
}
