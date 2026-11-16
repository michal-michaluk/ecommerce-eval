package shop.catalog.offer;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import shop.catalog.IntegrationTest;
import shop.catalog.PublishedEvents;
import shop.catalog.RecordedEvents;
import shop.catalog.Scopes;
import shop.catalog.tools.EventEnvelope;
import shop.catalog.tools.EventStream;

import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

/**
 * 01 RULE-4 ("draft content never leaves the context") and 13 RULE-1 ("nothing about drafts, reviews or quality findings ever
 * leaves") as one gate: a draft edit records no fact a publisher listens to and ships no public event, no unprivileged `GET` of 12
 * reaches a draft, and the service's own loggers write no line while a draft is created, edited or read.
 *
 * <p>Every absence here has a presence beside it: the publication at the end of the first test proves both captures observe a real
 * publication, the author's read of the same offer proves the content exists before the refusals are asserted, and a probe line
 * proves the log capture sees DEBUG before that capture is asserted. The offered draft carries an id that sorts before every id this
 * service mints ({@code AAA-}), so the list page the test asserts on holds it whatever else the shared database holds.
 */
@IntegrationTest
class DraftPrivacyTest {

    static final String DRAFT_TITLE = "SENTINEL unpublished draft title";
    static final String EDITED_TITLE = "SENTINEL edited draft title";
    static final String EDITED_TEXT = "SENTINEL unpublished draft paragraph";
    static final String MEDIA_URL = OfferFixture.mediaOfInstanceA().getFirst().url();

    @Autowired
    OfferService offers;
    @Autowired
    WebApplicationContext context;
    @Autowired
    RecordedEvents recorded;
    @MockitoSpyBean
    EventStream stream;

    MockMvc mvc;

    @BeforeEach
    void buildsMockMvc() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void publishesNoPublicEventWhenTheDraftIsEdited() {
        String offerId = draftedOffer();
        List<OfferEvent> factsBeforeTheEdit = recorded.of(OfferEvent.class);

        editTheDraft(offerId);

        assertThat(recorded.of(OfferEvent.class)).hasSize(factsBeforeTheEdit.size() + 1)
                .endsWith(new OfferEvent.OfferDraftUpdated(offerId));
        assertThat(recorded.of(OfferEvent.OfferVersionFrozen.class)).isEmpty();
        assertThat(recorded.of(OfferEvent.OfferRemoved.class)).isEmpty();
        assertThat(publishedTypes()).isEmpty();

        offers.publish(offerId, OfferFixture.now(), CatalogHttpFixture.REVIEWER_CALLER);

        assertThat(recorded.of(OfferEvent.OfferVersionFrozen.class)).hasSize(1);
        assertThat(publishedTypes()).containsExactly(OfferPublished.TYPE);
    }

    @Test
    void exposesNoDraftThroughTheGetsOf12() throws Exception {
        String offerId = draftedOffer();
        editTheDraft(offerId);

        for (String route : List.of("/offers/" + offerId, "/offers/" + offerId + "/draft")) {
            assertThat(responseOf(Scopes.AUTHOR, request(GET, route)).getStatus()).isEqualTo(200);
        }
        MockHttpServletResponse readByItsAuthor = responseOf(Scopes.AUTHOR, request(GET, "/offers/" + offerId + "/draft"));
        assertThat(readByItsAuthor.getContentAsString()).contains(EDITED_TITLE, EDITED_TEXT);

        for (String scope : List.of(Scopes.AUTHOR, Scopes.REVIEWER)) {
            MockHttpServletResponse listed = responseOf(scope, theList());
            assertThat(listed.getStatus()).isEqualTo(200);
            assertThat(CatalogHttpFixture.jsonList(listed, "$.items[*].offerId")).contains(offerId);
            assertThat(listed.getContentAsString()).doesNotContain(DRAFT_TITLE, EDITED_TITLE, EDITED_TEXT, MEDIA_URL);
        }

        for (String scope : List.of(Scopes.SALES, Scopes.PHOTOGRAPHER)) {
            MockHttpServletResponse listed = responseOf(scope, theList());
            assertThat(listed.getStatus()).isEqualTo(403);
            assertThat(listed.getContentAsString()).isEmpty();
        }

        for (String route : List.of("/offers", "/offers/" + offerId, "/offers/" + offerId + "/draft")) {
            MockHttpServletResponse refused = responseOf(Scopes.SALES, request(GET, route).param("page", "0").param("size", "1"));
            assertThat(refused.getStatus()).isEqualTo(403);
            assertThat(refused.getContentAsString()).isEmpty();
        }
    }

    @Test
    void writesNoDraftToTheLog() throws Exception {
        Logger service = (Logger) LoggerFactory.getLogger("shop.catalog");
        Level previous = service.getLevel();
        ListAppender<ILoggingEvent> capture = new ListAppender<>();
        capture.start();

        try {
            service.setLevel(Level.ALL);
            service.addAppender(capture);
            LoggerFactory.getLogger(DraftPrivacyTest.class).debug("capture probe");
            assertThat(capture.list).hasSize(1);
            capture.list.clear();

            String offerId = draftedOffer();
            editTheDraft(offerId);
            responseOf(Scopes.AUTHOR, request(GET, "/offers/" + offerId + "/draft"));

            assertThat(capture.list).extracting(ILoggingEvent::getFormattedMessage)
                    .noneMatch(line -> Stream.of(DRAFT_TITLE, EDITED_TITLE, EDITED_TEXT, MEDIA_URL, offerId)
                            .anyMatch(line::contains));
        } finally {
            service.detachAppender(capture);
            service.setLevel(previous);
            capture.stop();
        }
    }

    private String draftedOffer() {
        String offerId = "AAA-" + OfferDocumentFixture.uniqueOfferId();
        ProductDescription.Draft draft = ProductDescriptionDraftFixture
                .draftWithIdentifiers(OfferDocumentFixture.uniqueProductNumber(), OfferDocumentFixture.uniqueEanGtin())
                .toBuilder().title(DRAFT_TITLE).build();
        offers.draftOffer(offerId, draft);
        offers.updateMedia(offerId, OfferFixture.mediaOfInstanceA());
        offers.scheduleAvailability(offerId, OfferFixture.AVAILABILITY_START);
        return offerId;
    }

    private void editTheDraft(String offerId) {
        Offer edited = offers.updateDraft(offerId, ProductDescription.Draft.builder()
                .title(EDITED_TITLE)
                .blocks(List.of(new ProductDescription.DescriptionBlock.Paragraph(EDITED_TEXT)))
                .build(), null, null).orElseThrow();
        assertThat(edited.description().title()).isEqualTo(EDITED_TITLE);
    }

    private List<String> publishedTypes() {
        return PublishedEvents.of(stream).stream().map(EventEnvelope::eventType).toList();
    }

    private MockHttpServletResponse responseOf(String scope, MockHttpServletRequestBuilder request) throws Exception {
        return CatalogHttpFixture.as(scope, CatalogHttpFixture.SALES_CALLER, mvc, request);
    }

    private static MockHttpServletRequestBuilder theList() {
        return request(GET, "/offers").param("page", "0").param("size", "100");
    }
}
