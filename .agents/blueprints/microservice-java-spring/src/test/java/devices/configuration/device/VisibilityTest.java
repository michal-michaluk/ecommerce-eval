package devices.configuration.device;

import org.junit.jupiter.api.Test;

import static devices.configuration.device.Visibility.ForCustomer.*;
import static org.assertj.core.api.Assertions.assertThat;

class VisibilityTest {

    @Test
    void usableAndVisibleOnMap() {
        assertThat(Visibility.calculateVisibility(true, true))
                .isEqualTo(new Visibility(true, USABLE_AND_VISIBLE_ON_MAP));
    }

    @Test
    void usableButHiddenOnMap() {
        assertThat(Visibility.calculateVisibility(true, false))
                .isEqualTo(new Visibility(true, USABLE_BUT_HIDDEN_ON_MAP));
    }

    @Test
    void inaccessibleAndHiddenOnMap() {
        assertThat(Visibility.calculateVisibility(false, false))
                .isEqualTo(new Visibility(false, INACCESSIBLE_AND_HIDDEN_ON_MAP));
    }

    @Test
    void inaccessibleAndHiddenOnMapEvenWhenIntendedToShow() {
        assertThat(Visibility.calculateVisibility(false, true))
                .isEqualTo(new Visibility(false, INACCESSIBLE_AND_HIDDEN_ON_MAP));
    }
}
