package top.code2life.config;

import org.springframework.stereotype.Component;

import java.lang.annotation.*;

/**
 * The annotation @DynamicConfig could be added on Class or Field level,
 * to indicate that all @Value fields or single @Value field is dynamic loaded,
 * which means the actual value changes upon configuration file changes.
 * The processor of this annotation is not thread-safe, when configuration changes,
 * it will modify fields of the corresponding bean, during the period, dirty value could exist.
 *
 * Example:
 *
 * {@literal @}Component
 * {@literal @}DynamicConfig
 * class MyConfiguration {
 *     {@literal @}Value("${some.prop}")
 *     private String someProp;
 *
 *     {@literal @}Value("${another.prop}")
 *     {@literal @}DynamicConfig
 *     private Long anotherProp;
 * }
 *
 * Make sure you are using 'java -jar your-jar-file.jar --spring.config.location' to
 * start your application on none-local environments, or -Dspring.config.location before '-jar',
 * if this parameter set, file watch will be started to monitor properties/yml changes.
 *
 * @author Code2Life
 * @see DynamicConfigPropertiesWatcher
 */
@Target({ElementType.FIELD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Component
public @interface DynamicConfig {
}
