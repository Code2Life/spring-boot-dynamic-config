package top.code2life.config;

import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A util bean for simple feature gate implementation.
 * Step1:
 * {@literal @}Value("#{{@literal @}featureGate.convert('${dynamic-feature-conf}')}")
 * private Set{@literal <}String{@literal >} someBetaFeatureConfig;
 * Step2:
 * boolean featureEnabled = FeatureGate.isFeatureEnabled(dynamicConfigBean.someBetaFeatureConfig(), "someId")
 *
 * @author Code2Life
 **/
@Component("featureGate")
@RequiredArgsConstructor
public class FeatureGate {

    /**
     * If some feature is configured as "all", it means this feature is enabled
     * within all accounts/users/groups
     */
    public static final String FEATURE_ENABLE_FOR_ALL = "all";

    private static final String SEPARATOR_COMMA = ",";

    private final Environment environment;

    /**
     * Transform a comma separated string into a set,
     * indicate which entities enable that feature
     * eg:
     * someFeatureBetaList: userGroup1, userGroup2, ...
     *
     * @param val configuration value
     * @return a set of unique entity identifiers
     */
    public Set<String> convert(String val) {
        if (!StringUtils.hasText(val)) {
            return Collections.emptySet();
        }
        return Arrays.stream(val.split(SEPARATOR_COMMA)).map(StringUtils::trimWhitespace).filter(StringUtils::hasText).collect(Collectors.toSet());
    }


    /**
     * Judge if some entity is configured as enabling certain feature
     *
     * @param featureConfigValues the Set contains all entities which enable certain feature
     * @param entityId            the identifier of current requesting user/entity
     * @return if that feature enabled or not for certain user/account/entity
     */
    public boolean isFeatureEnabled(Set<String> featureConfigValues, String entityId) {
        return featureConfigValues.contains(FEATURE_ENABLE_FOR_ALL) || featureConfigValues.contains(entityId);
    }

    /**
     * Judge if some feature is enabled in configuration files
     * eg: my.feature.enabled=true / True / TRUE
     *
     * @param featureName feature name
     * @return if that feature is enabled or not
     */
    public boolean isFeatureEnabled(String featureName) {
        String configVal = environment.getProperty(featureName);
        if (StringUtils.hasText(configVal)) {
            return Boolean.parseBoolean(configVal);
        }
        return false;
    }
}
