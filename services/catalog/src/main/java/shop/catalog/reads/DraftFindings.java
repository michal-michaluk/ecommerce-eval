package shop.catalog.reads;

import shop.catalog.offer.Media;
import shop.catalog.offer.ProductDescription;

import java.util.List;

/**
 * The contract port that asks 08 what is wrong with a draft, so the quality panel of 14 can explain every blocked action inline
 * (D20, 17 {@code offer | quality} port, consulted for the screen).
 *
 * <p>It is asked while the read model is served, never when a fact is handled: a stored copy of the report would be a second answer
 * that drifts the moment a rule or its configuration changes, while 08 promises to run continuously on the draft. The listener side
 * therefore stays local — it writes its own row and asks nobody ({@code integration.listener-local-only}).
 *
 * <p>It answers, it never fails: a draft that breaks a rule is a legal draft, and the list of findings is the answer. The empty list
 * is the state 14 renders as ready.
 */
public interface DraftFindings {

    List<QualityFinding> findingsOf(ProductDescription.Draft draft, List<Media> media);
}
