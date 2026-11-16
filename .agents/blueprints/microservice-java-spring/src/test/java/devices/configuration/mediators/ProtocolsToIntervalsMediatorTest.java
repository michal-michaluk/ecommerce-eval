package devices.configuration.mediators;

import devices.configuration.communication.BootNotification;
import devices.configuration.communication.CommunicationFixture;
import devices.configuration.intervals.IntervalsService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProtocolsToIntervalsMediatorTest {

    @Mock
    IntervalsService intervals;

    @InjectMocks
    ProtocolsToIntervalsMediator mediator;

    @Test
    void shouldDelegateToIntervalsService() {
        BootNotification boot = CommunicationFixture.boot("mediator-device");
        when(intervals.calculateInterval(boot)).thenReturn(Duration.ofSeconds(300));

        Duration result = mediator.heartbeatIntervalFor(boot);

        assertThat(result).isEqualTo(Duration.ofSeconds(300));
    }
}
