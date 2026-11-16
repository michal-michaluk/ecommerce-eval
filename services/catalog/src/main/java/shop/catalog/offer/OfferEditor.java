package shop.catalog.offer;

import lombok.AllArgsConstructor;
import shop.catalog.offer.Offer.State;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * One offer being edited: its description draft, its media, its availability date, its lifecycle state and the versions it has
 * frozen. It is the only writer of all of them.
 *
 * <p>The editor holds the description in builder form ({@link ProductDescription.Draft}), never a frozen
 * {@link ProductDescription}, so an incomplete listing is a legal state and only the freeze refuses it (RULE-7, RULE-8).
 * Publishing embeds {@code draft.toDefinition()} by value into a new {@link OfferVersion} — no frozen version ever re-reads the
 * draft (RULE-4, RULE-10) — while a revert appends the next version number with an older version's content, so no number is ever
 * reused (RULE-5). Removal is terminal (RULE-6).
 *
 * <p>The uniqueness of {@code productNumber} and {@code eanGtin} across offers is the one rule a value object cannot enforce; the
 * aggregate asks {@link OfferRegistry} whenever an identifier enters the offer and again before it is frozen, and never asks a
 * repository (RULE-11).
 *
 * <p>Instances are created through {@link #draftAnOffer(String, ProductDescription.Draft, OfferRegistry)}; the aggregate is
 * package-private and appends every accepted change to its {@code events} buffer, which the persistence adapter drains.
 */
@AllArgsConstructor(access = lombok.AccessLevel.PACKAGE)
class OfferEditor {

    private static final int FIRST_VERSION = 1;

    final String offerId;

    final List<OfferEvent> events;

    private ProductDescription.Draft draft;
    private List<Media> media;
    private LocalDate availabilityStart;
    private State state;
    private List<OfferVersion> versions;
    private boolean reviewOpen;

    /** Opens the draft of an offer under {@code offerId}: the first version of the listing, not yet reviewed and not yet published. */
    static OfferEditor draftAnOffer(String offerId, ProductDescription.Draft firstDraft, OfferRegistry offers) {
        requireValue(offerId, "offerId");
        requireValue(firstDraft, "description");
        var editor = new OfferEditor(offerId, new ArrayList<>(), firstDraft,
                List.<Media>of(), null, State.DRAFT, new ArrayList<OfferVersion>(), false);
        editor.ensureIdentifiersAreFree(firstDraft.identifiers(), offers);
        editor.events.add(new OfferEvent.OfferDraftUpdated(offerId));
        return editor;
    }

    /**
     * Merges an edit into the draft: only the components the change names move, the rest keep their value (RULE-9).
     *
     * <p>A change that leaves the draft as it was is accepted silently and emits nothing.
     */
    void updateDescription(ProductDescription.Draft change, OfferRegistry offers) {
        requireValue(change, "description");
        ensureOfferIsNotRemoved();
        ProductDescription.Draft merged = draft.merge(change);
        ensureIdentifiersAreFree(merged.identifiers(), offers);
        if (!Objects.equals(draft, merged)) {
            this.draft = merged;
            events.add(new OfferEvent.OfferDraftUpdated(offerId));
        }
    }

    /** Replaces the media of the offer as the author ordered it; RULE-1 and RULE-2 are checked when a review is requested. */
    void updateMedia(List<Media> media) {
        requireValue(media, "media");
        ensureOfferIsNotRemoved();
        List<Media> updated = List.copyOf(media);
        if (!Objects.equals(this.media, updated)) {
            this.media = updated;
            events.add(new OfferEvent.OfferDraftUpdated(offerId));
        }
    }

    /** Records the date the product becomes available; it may lie in the future, and visibility decides what that means (RULE-3, 11). */
    void scheduleAvailability(LocalDate availabilityStart) {
        requireValue(availabilityStart, "availabilityStart");
        ensureOfferIsNotRemoved();
        if (!Objects.equals(this.availabilityStart, availabilityStart)) {
            this.availabilityStart = availabilityStart;
            events.add(new OfferEvent.OfferDraftUpdated(offerId));
        }
    }

    /**
     * Asks the reviewer to look at the draft; the media rules of RULE-1 and RULE-2 are the price of entering the review.
     *
     * <p>A published offer stays {@link State#PUBLISHED} while its next version is reviewed (D37): moving it to
     * {@link State#PENDING_REVIEW} would hide a live product from the shop, because 11 R3 hides that state. Only a draft enters the
     * review state; a live offer keeps serving its current version until an acceptance freezes the next one.
     */
    void requestReview() {
        ensureOfferIsNotRemoved();
        ensureMediaIsPublishable(media);
        if (reviewOpen) {
            return;
        }
        if (state == State.DRAFT) {
            this.state = State.PENDING_REVIEW;
        }
        this.reviewOpen = true;
        events.add(new OfferEvent.OfferReviewRequested(offerId));
    }

    /**
     * Freezes the draft into the next version and makes the offer published.
     *
     * <p>{@code draft.toDefinition()} is the only producer of the frozen value and it throws on a violated invariant, and the
     * population rules of RULE-11 are asked again here, so the freeze — not the draft — is where every rule bites (RULE-8,
     * RULE-10, RULE-11). A draft is a legal source state: 09 R1 publishes an accepted review for a {@code draft} offer too.
     */
    void publish(Instant publishedAt, String publishedBy, OfferRegistry offers) {
        requirePublicationDetails(publishedAt, publishedBy);
        ensureVersionCanBeFrozen(draft.identifiers(), media, availabilityStart, offers);
        freeze(new OfferCandidate(draft.toDefinition(), media, availabilityStart), publishedAt, publishedBy);
    }

    /**
     * Freezes the content 04 accepted: the candidate frozen at the request — its description by value, its media in the author's order
     * and its availability date — never the draft, which may have changed since (RULE-4, D38).
     *
     * <p>The draft keeps the author's current work, exactly as a revert leaves it: what was reviewed and what is published are the same
     * content, and an edit made after the request is a candidate for the <em>next</em> review. The invariants of RULE-1, RULE-2 and
     * RULE-11 are asked of that same content, because the freeze is where a version's rules bite.
     */
    void publishCandidate(OfferCandidate candidate, Instant publishedAt, String publishedBy, OfferRegistry offers) {
        requireValue(candidate, "candidate");
        requirePublicationDetails(publishedAt, publishedBy);
        ensureVersionCanBeFrozen(
                candidate.description().identifiers(), candidate.media(), candidate.availabilityStart(), offers);
        freeze(candidate, publishedAt, publishedBy);
    }

    private void freeze(OfferCandidate candidate, Instant publishedAt, String publishedBy) {
        OfferVersion frozen = new OfferVersion(nextVersionNumber(), candidate.description(), candidate.media(),
                candidate.availabilityStart(), publishedAt, publishedBy, null);
        versions.add(frozen);
        this.state = State.PUBLISHED;
        this.reviewOpen = false;
        events.add(new OfferEvent.OfferVersionFrozen(offerId, frozen.number(), frozen.description()));
    }

    /**
     * Republishes the content of an older version as a new one: the source version is copied, its number is never reused and the
     * draft keeps the author's current work (RULE-5).
     *
     * <p>An open review blocks the revert, because 04 froze a candidate for it and changing what is published underneath would
     * make "what was reviewed" and "what is published" two different things.
     */
    void revert(int sourceVersion, Instant publishedAt, String publishedBy, OfferRegistry offers) {
        requirePublicationDetails(publishedAt, publishedBy);
        OfferVersion source = version(sourceVersion);
        ensureRevertIsAllowed(source, offers);
        OfferVersion reverted = source.copiedAs(nextVersionNumber(), publishedAt, publishedBy);
        versions.add(reverted);
        this.state = State.PUBLISHED;
        events.add(new OfferEvent.OfferVersionReverted(offerId, reverted.number(), sourceVersion));
    }

    /** Withdraws the product for good: only a published offer can be removed and nothing can follow it (RULE-6, Q11). */
    void remove() {
        ensureOfferIsPublished();
        this.state = State.REMOVED;
        events.add(new OfferEvent.OfferRemoved(offerId));
    }

    /** The editing content no snapshot carries: the draft and its media, as the persistence adapter stores it ({@link OfferSurface}). */
    OfferSurface toSurface() {
        OfferSurface surface = new OfferSurface(draft, media, reviewOpen);
        return surface;
    }

    /**
     * The content a review candidate freezes at the request (D38), as the consumer's port reads it.
     *
     * <p>It refuses an offer whose description cannot be a value or whose availability date is not scheduled yet: a candidate carries
     * both by value, and the refusal is this context's own — a draft that never published anything is exactly the case RULE-3 leaves
     * undefined for a version.
     */
    OfferCandidate toCandidate() {
        ensureAvailabilityIsScheduled(availabilityStart);
        return new OfferCandidate(draft.toDefinition(), media, availabilityStart);
    }

    /**
     * The offer as the read side serves it: identity, state, the number of the version in force, the availability date and the
     * surface the draft screen renders — the draft {@code reads} copies with its media (14).
     */
    Offer toOffer() {
        int currentVersion = versions.size();
        return new Offer(offerId, state, currentVersion, availabilityStart, draft, media);
    }

    /** The frozen version {@code number}; a revert reads its source through this query. */
    OfferVersion version(int number) {
        return versions.stream()
                .filter(candidate -> candidate.number() == number)
                .findFirst()
                .orElseThrow(() -> new OfferException(OfferException.Error.OFFER_VERSION_MISSING, offerId, number));
    }

    private int nextVersionNumber() {
        return versions.size() + FIRST_VERSION;
    }

    private void ensureOfferIsNotRemoved() {
        if (state == State.REMOVED) {
            throw new OfferException(OfferException.Error.OFFER_REMOVED, offerId);
        }
    }

    private void ensureOfferIsPublished() {
        if (state != State.PUBLISHED) {
            throw new OfferException(OfferException.Error.REMOVAL_NEEDS_PUBLICATION, offerId, state);
        }
    }

    private void ensureNoReviewIsOpen() {
        if (reviewOpen) {
            throw new OfferException(OfferException.Error.REVIEW_OPEN, offerId);
        }
    }

    private void ensureVersionCanBeFrozen(
            ProductDescription.Identifiers identifiers, List<Media> media, LocalDate availabilityStart, OfferRegistry offers) {
        ensureOfferIsNotRemoved();
        ensureMediaIsPublishable(media);
        ensureAvailabilityIsScheduled(availabilityStart);
        ensureIdentifiersAreFree(identifiers, offers);
    }

    private void ensureRevertIsAllowed(OfferVersion source, OfferRegistry offers) {
        ensureOfferIsNotRemoved();
        ensureNoReviewIsOpen();
        ensureIdentifiersAreFree(source.description().identifiers(), offers);
    }

    private void ensureAvailabilityIsScheduled(LocalDate availabilityStart) {
        if (availabilityStart == null) {
            throw new OfferException(OfferException.Error.AVAILABILITY_START_MISSING, offerId);
        }
    }

    private void ensureMediaIsPublishable(List<Media> media) {
        ensureMediaHasOnePrimaryImage(media);
        ensurePositionsAreContiguous(media);
        ensureAtMostOneVideo(media);
    }

    private void ensureMediaHasOnePrimaryImage(List<Media> media) {
        if (media.stream().noneMatch(Media::isImage)) {
            throw new OfferException(OfferException.Error.MEDIA_IMAGE_MISSING, offerId);
        }
        long primaryImages = media.stream().filter(Media::isImage).filter(Media::primary).count();
        if (primaryImages != 1) {
            throw new OfferException(OfferException.Error.MEDIA_PRIMARY_AMBIGUOUS, offerId, primaryImages);
        }
    }

    private void ensurePositionsAreContiguous(List<Media> media) {
        List<Integer> positions = media.stream().map(Media::position).sorted().toList();
        for (int index = 0; index < positions.size(); index++) {
            if (positions.get(index) != index + 1) {
                throw new OfferException(OfferException.Error.MEDIA_POSITIONS_NOT_CONTIGUOUS, offerId, positions);
            }
        }
    }

    private void ensureAtMostOneVideo(List<Media> media) {
        long videos = media.stream().filter(Media::isVideo).count();
        if (videos > 1) {
            throw new OfferException(OfferException.Error.MEDIA_VIDEO_COUNT, offerId, videos);
        }
    }

    private void ensureIdentifiersAreFree(ProductDescription.Identifiers identifiers, OfferRegistry offers) {
        if (identifiers != null) {
            ensureProductNumberIsFree(identifiers.productNumber(), offers);
            ensureEanGtinIsFree(identifiers.eanGtin(), offers);
        }
    }

    private void ensureProductNumberIsFree(String productNumber, OfferRegistry offers) {
        if (productNumber != null && !productNumber.isBlank() && offers.isProductNumberTakenByOther(productNumber, offerId)) {
            throw new OfferException(OfferException.Error.PRODUCT_NUMBER_TAKEN, productNumber);
        }
    }

    private void ensureEanGtinIsFree(String eanGtin, OfferRegistry offers) {
        if (eanGtin != null && !eanGtin.isBlank() && offers.isEanGtinTakenByOther(eanGtin, offerId)) {
            throw new OfferException(OfferException.Error.EAN_GTIN_TAKEN, eanGtin);
        }
    }

    private static void requireValue(Object value, String name) {
        if (value == null) {
            throw new OfferException(OfferException.Error.COMMAND_VALUE_MISSING, name);
        }
    }

    private static void requirePublicationDetails(Instant publishedAt, String publishedBy) {
        requireValue(publishedAt, "publishedAt");
        requireValue(publishedBy, "publishedBy");
    }
}
