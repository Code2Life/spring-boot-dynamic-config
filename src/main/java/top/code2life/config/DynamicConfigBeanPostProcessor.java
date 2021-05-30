package top.code2life.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.util.StringUtils;

import javax.annotation.PostConstruct;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static top.code2life.config.ConfigurationUtils.SP_EL_PREFIX;
import static top.code2life.config.ConfigurationUtils.VALUE_EXPR_PREFIX;

/**
 * PostProcessor for any bean which has annotation @DynamicConfig on its Class/Field,
 * The processor will maintain field accessor for @Value fields, listen ConfigurationChangedEvent,
 * If any event arrives, it will retrieve diff results of changed properties, evaluate SpEL and set field values.
 *
 * @author Code2Life
 */
@Slf4j
@ConditionalOnBean(DynamicConfigPropertiesWatcher.class)
public class DynamicConfigBeanPostProcessor implements BeanPostProcessor {

    static final Map<String, List<ValueBeanFieldBinder>> DYNAMIC_FIELD_BINDER_MAP = new ConcurrentHashMap<>(16);
    static final Map<String, ValueBeanFieldBinder> DYNAMIC_CONFIG_PROPS_BINDER_MAP = new ConcurrentHashMap<>(8);
    static final Map<String, Object> DYNAMIC_BEAN_MAP = new ConcurrentHashMap<>(16);

    private final ApplicationContext applicationContext;

    DynamicConfigBeanPostProcessor(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
        DYNAMIC_BEAN_MAP.clear();
        DYNAMIC_FIELD_BINDER_MAP.clear();
        DYNAMIC_CONFIG_PROPS_BINDER_MAP.clear();
    }

    /**
     * Check all beans with DynamicConfig annotation after this PostProcessor being initialized,
     * this double check mechanism is for a special case: some beans created by other approaches directly, not calling BeanPostProcessor
     */
    @PostConstruct
    public void handleDynamicConfigurationBeans() {
        Map<String, Object> dynamicBeans = applicationContext.getBeansWithAnnotation(DynamicConfig.class);
        for (Map.Entry<String, Object> entry : dynamicBeans.entrySet()) {
            String beanName = entry.getKey();
            handleDynamicBean(entry.getValue(), beanName);
        }
    }

    /**
     * Process all beans contains @DynamicConfig annotation, collect metadata for continuous field value binding
     *
     * @param bean     bean instance
     * @param beanName bean name
     * @return original bean instance
     */
    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) {
        handleDynamicBean(bean, beanName);
        return bean;
    }

    private void handleDynamicBean(Object bean, String beanName) {
        // avoid duplicate processing after post processor
        if (DYNAMIC_BEAN_MAP.containsKey(beanName)) {
            return;
        }
        DYNAMIC_BEAN_MAP.putIfAbsent(beanName, bean);
        Class<?> clazz = ConfigurationUtils.getTargetClassOfBean(bean);
        Field[] fields = clazz.getDeclaredFields();
        // handle @ConfigurationProperties beans
        boolean clazzLevelDynamicConf = clazz.isAnnotationPresent(DynamicConfig.class);
        if (clazzLevelDynamicConf && clazz.isAnnotationPresent(ConfigurationProperties.class)) {
            bindConfigurationProperties(clazz, bean, beanName);
            return;
        }
        // handle beans contains @Value + @DynamicConfig annotations
        for (Field field : fields) {
            boolean isDynamic = field.isAnnotationPresent(Value.class) && (clazzLevelDynamicConf || field.isAnnotationPresent(DynamicConfig.class));
            if (isDynamic) {
                collectionValueAnnotationMetadata(bean, beanName, clazz, field);
            }
        }
    }

    private void bindConfigurationProperties(Class<?> clazz, Object bean, String beanName) {
        ConfigurationProperties properties = clazz.getAnnotation(ConfigurationProperties.class);
        String prefix = properties.prefix();
        if (!StringUtils.hasText(prefix)) {
            prefix = properties.value();
        }
        prefix = ConfigurationUtils.normalizePropKey(prefix);
        ValueBeanFieldBinder binder = new ValueBeanFieldBinder(prefix, null, bean, beanName);
        DYNAMIC_CONFIG_PROPS_BINDER_MAP.putIfAbsent(prefix, binder);
    }

    private void collectionValueAnnotationMetadata(Object bean, String beanName, Class<?> clazz, Field field) {
        String valueExpr = field.getAnnotation(Value.class).value();
        if (!valueExpr.startsWith(VALUE_EXPR_PREFIX) && !valueExpr.startsWith(SP_EL_PREFIX)) {
            return;
        }
        List<String> propKeyList = ConfigurationUtils.extractValueFromExpr(valueExpr);
        for (String key : propKeyList) {
            if (!DYNAMIC_FIELD_BINDER_MAP.containsKey(key)) {
                DYNAMIC_FIELD_BINDER_MAP.putIfAbsent(key, Collections.synchronizedList(new ArrayList<>(2)));
            }
            DYNAMIC_FIELD_BINDER_MAP.get(key).add(new ValueBeanFieldBinder(valueExpr, field, bean, beanName));
        }
        if (propKeyList.size() > 0 && log.isDebugEnabled()) {
            log.debug("dynamic config annotation found on class: {}, field: {}, prop: {}", clazz.getName(), field.getName(), String.join(",", propKeyList));
        }
    }
}
