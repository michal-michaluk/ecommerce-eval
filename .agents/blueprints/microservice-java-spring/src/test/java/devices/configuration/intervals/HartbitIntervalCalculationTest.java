package devices.configuration.intervals;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

class HartbitIntervalCalculationTest {

    HartbitIntervalCalculation rules = HartbitIntervalCalculationFixture.currentRules();

    @Test
    void matchInFirstDeviceIdRule() {
        var boot = HartbitIntervalCalculationFixture.matchingDeviceIdRule1();

        var interval = rules.calculateInterval(boot);

        Assertions.assertThat(interval).hasSeconds(600);
    }

    @Test
    void matchInSecondDeviceIdRule() {
        var boot = HartbitIntervalCalculationFixture.matchingDeviceIdRule2();

        var interval = rules.calculateInterval(boot);

        Assertions.assertThat(interval).hasSeconds(2700);
    }

    @Test
    void matchInStrictModelRule() {
        var boot = HartbitIntervalCalculationFixture.matchingStrictModelRule();

        var interval = rules.calculateInterval(boot);

        Assertions.assertThat(interval).hasSeconds(60);
    }

    @Test
    void matchInRegexpFirmwareRule() {
        var boot = HartbitIntervalCalculationFixture.matchingRegexFirmwareRule();

        var interval = rules.calculateInterval(boot);

        Assertions.assertThat(interval).hasSeconds(10);
    }

    @Test
    void matchInRegexpModelRule() {
        var boot = HartbitIntervalCalculationFixture.matchingRegexModelRule();

        var interval = rules.calculateInterval(boot);

        Assertions.assertThat(interval).hasSeconds(120);
    }

    @Test
    void returnDefaultInterval() {
        var boot = HartbitIntervalCalculationFixture.notMatchingAnyRule();

        var interval = rules.calculateInterval(boot);

        Assertions.assertThat(interval).hasSeconds(1800);
    }
}
