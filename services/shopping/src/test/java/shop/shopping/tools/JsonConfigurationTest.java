package shop.shopping.tools;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class JsonConfigurationTest {

    record Amount(BigDecimal value) {
    }

    @Test
    void writesBigDecimalWithoutExponent() throws Exception {
        String json = JsonConfiguration.OBJECT_MAPPER.writeValueAsString(new Amount(new BigDecimal("1000000.10")));

        assertThat(json).isEqualTo("{\"value\":1000000.10}");
    }

    @Test
    void ignoresUnknownProperties() throws Exception {
        Amount amount = JsonConfiguration.OBJECT_MAPPER.readValue("{\"value\":1.5,\"unknown\":true}", Amount.class);

        assertThat(amount.value()).isEqualByComparingTo("1.5");
    }
}
