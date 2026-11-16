package devices.configuration.intervals;

import tools.jackson.databind.JsonNode;
import devices.configuration.tools.FeatureConfiguration;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@AllArgsConstructor
class IntervalRulesDocumentRepository implements IntervalRulesRepository {

    public static final String CONFIG_NAME = "IntervalRules";
    private final FeatureConfiguration repository;

    @Override
    public HartbitIntervalCalculation get() {
        try {
            return repository.get(CONFIG_NAME)
                    .as(HartbitIntervalCalculation.class)
                    .orElse(HartbitIntervalCalculation.defaultRules());
        } catch (RuntimeException unreadable) {
            throw new IntervalsException(IntervalsException.Error.CONFIGURATION_UNREADABLE, unreadable, CONFIG_NAME);
        }
    }

    public JsonNode save(HartbitIntervalCalculation configuration) {
        return repository.save(CONFIG_NAME, configuration)
                .raw();
    }
}
