package shop.catalog.offer;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    public Offer draftOffer(String offerId, ProductDescription.Draft description) {
        ensureNoOfferExists(offerId);
        OfferEditor offer = OfferEditor.draftAnOffer(offerId, description, registry);
        offers.save(offer);
        return offer.toOffer();
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
}
