package top.code2life.config;

import org.springframework.context.annotation.Import;

/**
 * @author Code2Life
 */
@Import({DynamicConfigPropertiesWatcher.class, DynamicConfigBeanPostProcessor.class, FeatureGate.class, ConfigurationChangedEventHandler.class})
public class DynamicConfigAutoConfiguration {
}
