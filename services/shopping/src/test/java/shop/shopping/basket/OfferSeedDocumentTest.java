package shop.shopping.basket;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ByteArrayResource;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The seed this deployment actually carries: {@code k8s/base/configmap.yaml} is parsed and bound with the machinery the pod
 * uses, so the document that answers element 08's adapter is asserted here rather than discovered on a cluster.
 *
 * <p>Without this, the seed is the one part of the contract nothing gates: a key that binds to nothing, a missing price or a
 * queue the fixture cannot read would surface as {@code 404 OFFER_NOT_FOUND} on step 3 or as an {@code outOfStock} row one
 * step early, which reads like a basket bug. Binding the shipped document makes the failure name the file — and it is the
 * authority for the prices in {@code docs/offer-fixture.md}, which only mirrors it.
 *
 * <p>The queue the document states is pinned, not just the offers: {@code OF-1002} answers nine units for nine reads and none
 * from the tenth, which is the transition the contract's step 15 needs, and the count is only right while this many reads land
 * before it (see {@code services/shopping/docs/offer-fixture.md} §The answer queue).
 */
class OfferSeedDocumentTest {

    private static final Path CONFIG_MAP = Path.of("k8s/base/configmap.yaml");
    private static final String SEED_KEY = "offer-fixture.yaml";
    private static final String OF_1001 = "OF-1001";
    private static final String OF_1002 = "OF-1002";
    private static final String OF_1003 = "OF-1003";
    private static final String OF_2007 = "OF-2007";
    private static final String OF_9001 = "OF-9001";

    @Test
    void bindsTheOffersTheDeploymentSeeds() throws IOException {
        assertThat(offerings()).extracting(FixtureOfferCatalog.Offering::offerId)
                .containsExactly(OF_1001, OF_1002, OF_2007, OF_9001, OF_1003);
    }

    @Test
    void seedsTheOneOfferWhoseProviderRefusesTheCall() throws IOException {
        FixtureOfferCatalog.Offering seeded = offering(OF_1003);

        assertThat(seeded.fault()).isEqualTo(FixtureOfferCatalog.Failure.serverError);
        assertThat(seeded.faultAfter()).isEqualTo(2);
        assertThat(seeded.description()).satisfies(described -> {
            assertThat(described.seller()).isEqualTo("UGREEN_POLSKA");
            assertThat(described.currentPrice().amount()).isEqualTo(79900);
        });
        assertThat(offerings()).filteredOn(offering -> offering.fault() != FixtureOfferCatalog.Failure.none)
                .extracting(FixtureOfferCatalog.Offering::offerId)
                .containsExactly(OF_1003);
    }

    @Test
    void seedsTheRowThatRunsOutOfStockOnItsTenthRead() throws IOException {
        assertThat(offering(OF_1002).stockAnswers()).containsExactly(
                new OfferObservation.Stock.Quantity(9),
                new OfferObservation.Stock.Quantity(9),
                new OfferObservation.Stock.Quantity(9),
                new OfferObservation.Stock.Quantity(9),
                new OfferObservation.Stock.Quantity(9),
                new OfferObservation.Stock.Quantity(9),
                new OfferObservation.Stock.Quantity(9),
                new OfferObservation.Stock.Quantity(9),
                new OfferObservation.Stock.Quantity(9),
                new OfferObservation.Stock.Quantity(0));
    }

    @Test
    void seedsTheRowThatPricesTheBasketUntilThen() throws IOException {
        FixtureOfferCatalog.Offering seeded = offering(OF_1001);

        assertThat(seeded.offerState()).isEqualTo(OfferObservation.OfferState.onSale);
        assertThat(seeded.stockAnswers()).containsExactly(new OfferObservation.Stock.Quantity(9));
        assertThat(seeded.description()).satisfies(described -> {
            assertThat(described.seller()).isEqualTo("UGREEN_POLSKA");
            assertThat(described.currentPrice().amount()).isEqualTo(99890);
            assertThat(described.discount().amount()).isZero();
        });
    }

    @Test
    void seedsTheRemovedOfferWithThePriceItStillNames() throws IOException {
        FixtureOfferCatalog.Offering seeded = offering(OF_2007);

        assertThat(seeded.offerState()).isEqualTo(OfferObservation.OfferState.removed);
        assertThat(seeded.description()).satisfies(described -> {
            assertThat(described.seller()).isEqualTo("ANKER_STORE");
            assertThat(described.currentPrice().amount()).isEqualTo(129000);
        });
    }

    @Test
    void seedsTheForeignCurrencyProbe() throws IOException {
        assertThat(offering(OF_9001).description().currentPrice().currency()).isEqualTo("EUR");
    }

    @Test
    void seedsNoOfferTheContractNeverNames() throws IOException {
        assertThat(offerings()).extracting(FixtureOfferCatalog.Offering::offerId)
                .doesNotContain("OF-0000");
    }

    private static FixtureOfferCatalog.Offering offering(String offerId) throws IOException {
        return offerings().stream().filter(offering -> offering.offerId().equals(offerId)).findFirst()
                .orElseThrow(() -> new AssertionError("the seed states no " + offerId));
    }

    private static List<FixtureOfferCatalog.Offering> offerings() throws IOException {
        return BasketConfiguration.offeringsOf(asBasketConfigurationBindsIt());
    }

    private static BasketConfiguration.OfferFixture asBasketConfigurationBindsIt() throws IOException {
        PropertySource<?> seed = new YamlPropertySourceLoader()
                .load(SEED_KEY, new ByteArrayResource(seedDocument().getBytes(StandardCharsets.UTF_8)))
                .get(0);
        return new Binder(ConfigurationPropertySources.from(seed))
                .bind("basket.offer-fixture", BasketConfiguration.OfferFixture.class)
                .orElseThrow(() -> new AssertionError("the ConfigMap carries no basket.offer-fixture block"));
    }

    private static String seedDocument() throws IOException {
        Map<String, Object> configMap = new Yaml().load(Files.newBufferedReader(CONFIG_MAP));
        return (String) data(configMap).get(SEED_KEY);
    }

    private static Map<?, ?> data(Map<String, Object> configMap) {
        Map<?, ?> data = (Map<?, ?>) configMap.get("data");
        if (data == null || !(data.get(SEED_KEY) instanceof String)) {
            throw new AssertionError(CONFIG_MAP + " carries no data." + SEED_KEY);
        }
        return data;
    }
}
