package top.code2life.config;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A util bean for simple feature gate implementation.
 * Step1:
 * {@literal @}Value("#{{@literal @}featureGate.convert('${dynamic-feature-conf}')}")
 * private Set<String> someBetaFeatureConfig;
 * Step2:
 * boolean featureEnabled = FeatureGate.isFeatureEnabled(dynamicConfigBean.someBetaFeatureConfig(), "someId")
 *
 * @author Code2Life
 **/
@Component
public class FeatureGate {

    public static final String FEATURE_ENABLE_FOR_ALL = "all";
    public static final String SEPARATOR_COMMA = ",";

    public Set<String> convert(String val) {
        if (!StringUtils.hasText(val)) {
            return Collections.emptySet();
        }
        return Arrays.stream(val.split(SEPARATOR_COMMA)).map(StringUtils::trimWhitespace).filter(StringUtils::hasText).collect(Collectors.toSet());
    }

    public Set<String> convert(Collection<String> val) {
        if (val == null || val.size() == 0) {
            return Collections.emptySet();
        }
        return val.stream().map(StringUtils::trimWhitespace).filter(StringUtils::hasText).collect(Collectors.toSet());
    }

    public static boolean isFeatureEnabled(Set<String> enabledConfigValues, String entityId) {
        return enabledConfigValues.contains(FEATURE_ENABLE_FOR_ALL) || enabledConfigValues.contains(entityId);
    }
}
