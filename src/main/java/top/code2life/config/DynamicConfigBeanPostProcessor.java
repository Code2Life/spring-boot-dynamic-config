package top.code2life.config;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.BeansException;
import org.springframework.beans.TypeConverter;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.BeanExpressionContext;
import org.springframework.beans.factory.config.BeanExpressionResolver;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.ConfigurationPropertiesBindingPostProcessor;
import org.springframework.boot.origin.OriginTrackedValue;
import org.springframework.context.ApplicationContext;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.util.ClassUtils;
import org.springframework.util.StringUtils;

import javax.annotation.PostConstruct;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * PostProcessor for any bean which has annotation @DynamicConfig on its Class/Field,
 * The processor will maintain field accessor for @Value fields, listen ConfigurationChangedEvent,
 * If any event arrives, it will retrieve diff results of changed properties, evaluate SpEL and set field values.
 *
 * @author Code2Life
 */
@Slf4j
@Component
@ConditionalOnBean(DynamicConfigPropertiesWatcher.class)
public class DynamicConfigBeanPostProcessor implements BeanPostProcessor {

    private static final String VALUE_EXPR_PREFIX = "$";
    private static final String SP_EL_PREFIX = "#";
    private static final Pattern VALUE_PATTERN = Pattern.compile("\\$\\{([^:}]+):?([^}]*)}");
    private static final Pattern CAMEL_CASE_PATTERN = Pattern.compile("([^A-Z-])([A-Z])");
    private static final Map<String, List<ValueBeanFieldBinder>> DYNAMIC_FIELD_BINDER_MAP = new ConcurrentHashMap<>(16);
    private static final Map<String, ValueBeanFieldBinder> DYNAMIC_CONFIG_PROPS_BINDER_MAP = new ConcurrentHashMap<>(8);
    private static final Map<String, Object> DYNAMIC_BEAN_MAP = new ConcurrentHashMap<>(16);

    private final BeanExpressionResolver exprResolver;
    private final BeanExpressionContext exprContext;
    private final ConfigurableListableBeanFactory beanFactory;
    private final ApplicationContext applicationContext;
    private final ConfigurationPropertiesBindingPostProcessor processor;

    public DynamicConfigBeanPostProcessor(ApplicationContext applicationContext, BeanFactory beanFactory) {
        if (!(beanFactory instanceof ConfigurableListableBeanFactory)) {
            throw new IllegalArgumentException(
                    "DynamicConfig requires a ConfigurableListableBeanFactory");
        }
        DYNAMIC_BEAN_MAP.clear();
        DYNAMIC_FIELD_BINDER_MAP.clear();
        DYNAMIC_CONFIG_PROPS_BINDER_MAP.clear();
        this.beanFactory = (ConfigurableListableBeanFactory) beanFactory;
        this.exprResolver = ((ConfigurableListableBeanFactory) beanFactory).getBeanExpressionResolver();
        this.exprContext = new BeanExpressionContext((ConfigurableListableBeanFactory) beanFactory, null);
        this.applicationContext = applicationContext;
        this.processor = applicationContext.getBean(ConfigurationPropertiesBindingPostProcessor.class);
    }

    @PostConstruct
    public void handleDynamicConfigurationBeans() throws BeansException {
        Map<String, Object> dynamicBeans = applicationContext.getBeansWithAnnotation(DynamicConfig.class);
        for (Map.Entry<String, Object> entry : dynamicBeans.entrySet()) {
            String beanName = entry.getKey();
            handleDynamicBean(entry.getValue(), beanName);
        }
    }

    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
        handleDynamicBean(bean, beanName);
        return bean;
    }

    private void handleDynamicBean(Object bean, String beanName) {
        // avoid duplicate processing after post processor
        if (DYNAMIC_BEAN_MAP.containsKey(beanName)) {
            return;
        }
        DYNAMIC_BEAN_MAP.putIfAbsent(beanName, bean);
        Class<?> clazz = AopUtils.getTargetClass(bean);
        if (clazz.getName().contains(ClassUtils.CGLIB_CLASS_SEPARATOR)) {
            clazz = clazz.getSuperclass();
        }
        Field[] fields = clazz.getDeclaredFields();
        boolean clazzLevelDynamicConf = clazz.isAnnotationPresent(DynamicConfig.class);
        if (clazzLevelDynamicConf && clazz.isAnnotationPresent(ConfigurationProperties.class)) {
            bindConfigurationProperties(clazz, bean, beanName);
            return;
        }
        for (Field f : fields) {
            boolean isDynamic = f.isAnnotationPresent(Value.class) && (clazzLevelDynamicConf || f.isAnnotationPresent(DynamicConfig.class));
            if (isDynamic) {
                String valueExpr = f.getAnnotation(Value.class).value();
                if (valueExpr.startsWith(VALUE_EXPR_PREFIX) || valueExpr.startsWith(SP_EL_PREFIX)) {
                    List<String> propKeyList = extractValueFromExpr(valueExpr);
                    for (String key : propKeyList) {
                        if (!DYNAMIC_FIELD_BINDER_MAP.containsKey(key)) {
                            DYNAMIC_FIELD_BINDER_MAP.putIfAbsent(key, Collections.synchronizedList(new ArrayList<>(2)));
                        }
                        DYNAMIC_FIELD_BINDER_MAP.get(key).add(new ValueBeanFieldBinder(valueExpr, f, bean, beanName));
                    }
                    if (propKeyList.size() > 0 && log.isDebugEnabled()) {
                        log.debug("dynamic config annotation found on class: {}, field: {}, prop: {}", clazz.getName(), f.getName(), String.join(",", propKeyList));
                    }
                }
            }
        }
    }

    private void bindConfigurationProperties(Class<?> clazz, Object bean, String beanName) {
        ConfigurationProperties properties = clazz.getAnnotation(ConfigurationProperties.class);
        String prefix = properties.prefix();
        if (!StringUtils.hasText(prefix)) {
            prefix = properties.value();
        }
        DYNAMIC_CONFIG_PROPS_BINDER_MAP.putIfAbsent(prefix, new ValueBeanFieldBinder(null, null, bean, beanName));
    }

    @EventListener
    @SuppressWarnings("unchecked")
    public void handleEvent(ConfigurationChangedEvent event) {
        try {
            Map<Object, OriginTrackedValue> prev = (Map<Object, OriginTrackedValue>) event.getPrevious().getSource();
            Map<Object, OriginTrackedValue> current = (Map<Object, OriginTrackedValue>) event.getCurrent().getSource();
            Map<Object, Object> diff = getPropertyDiff(prev, current);
            Map<String, ValueBeanFieldBinder> toRefreshProps = new HashMap<>(4);
            for (Map.Entry<Object, Object> entry : diff.entrySet()) {
                String key = entry.getKey().toString();
                Object val = entry.getValue();
                processConfigPropsClass(toRefreshProps, key);
                processValueField(key, val);
            }
            toRefreshProps.forEach((beanName, binder) -> {
                WeakReference<Object> beanRef = binder.getBeanRef();
                if (beanRef != null && beanRef.get() != null) {
                    processor.postProcessBeforeInitialization(Objects.requireNonNull(beanRef.get()), beanName);
                    log.debug("changes detected, re-bind ConfigurationProperties bean: {}", beanName);
                }
            });
            log.info("config changes of {} have been processed", event.getSource());
        } catch (Exception ex) {
            log.warn("config changes of {} can not be processed, error:", event.getSource(), ex);
            if (log.isDebugEnabled()) {
                log.error("error detail is:", ex);
            }
        }
    }

    private void processConfigPropsClass(Map<String, ValueBeanFieldBinder> result, String key) {
        DYNAMIC_CONFIG_PROPS_BINDER_MAP.forEach((prefix, binder) -> {
            if (StringUtils.startsWithIgnoreCase(normalizePropKey(key), normalizePropKey(prefix))) {
                log.debug("prefix matched for ConfigurationProperties bean: {}, prefix: {}", binder.getBeanName(), prefix);
                result.put(binder.getBeanName(), binder);
            }
        });
    }

    private void processValueField(String keyRaw, Object val) throws IllegalAccessException {
        String key = normalizePropKey(keyRaw);
        if (!DYNAMIC_FIELD_BINDER_MAP.containsKey(key)) {
            log.debug("no bound field of changed property found, skip dynamic config processing of key: {}", keyRaw);
            return;
        }
        List<ValueBeanFieldBinder> valueFieldBinders = DYNAMIC_FIELD_BINDER_MAP.get(key);
        for (ValueBeanFieldBinder binder : valueFieldBinders) {
            Object bean = binder.getBeanRef().get();
            if (bean != null) {
                Field field = binder.getDynamicField();
                field.setAccessible(true);
                String expr = binder.getExpr();
                String newExpr = beanFactory.resolveEmbeddedValue(expr);
                Object setVal = val;
                if (expr.startsWith(SP_EL_PREFIX)) {
                    setVal = exprResolver.evaluate(newExpr, exprContext);
                }
                field.set(bean, convertIfNecessary(field, setVal));
                if (log.isDebugEnabled()) {
                    log.debug("dynamic config found, set field: '{}' of class: '{}' with new value: {}", field.getName(), bean.getClass().getSimpleName(), setVal);
                }
            }
        }
    }

    /**
     * loop current properties, if add new property or existing value changes, it will be added to diff list
     * removed properties won't impact existing bean values
     */
    private Map<Object, Object> getPropertyDiff(Map<Object, OriginTrackedValue> prev, Map<Object, OriginTrackedValue> current) {
        Map<Object, Object> diff = new HashMap<>(4);
        for (Map.Entry<Object, OriginTrackedValue> entry : current.entrySet()) {
            Object k = entry.getKey();
            OriginTrackedValue v = entry.getValue();
            if (prev.containsKey(k)) {
                if (!Objects.equals(v, prev.get(k))) {
                    diff.put(k, v.getValue());
                    if (log.isDebugEnabled()) {
                        log.debug("found changed key of dynamic config: {}", k);
                    }
                }
            } else {
                diff.put(k, v.getValue());
                if (log.isDebugEnabled()) {
                    log.debug("found new added key of dynamic config: {}", k);
                }
            }
        }
        return diff;
    }

    private List<String> extractValueFromExpr(String valueExpr) {
        List<String> keys = new ArrayList<>(2);
        Matcher matcher = VALUE_PATTERN.matcher(valueExpr);
        while (matcher.find()) {
            try {
                // normalized into kebab case (abc-def.g-h)
                keys.add(normalizePropKey(matcher.group(1).trim()));
            } catch (Exception ex) {
                log.warn("can not extract target property from @Value declaration, expr: {}. error: {}", valueExpr, ex.getMessage());
            }
        }
        return keys;
    }

    private Object convertIfNecessary(Field field, Object value) {
        TypeConverter converter = beanFactory.getTypeConverter();
        return converter.convertIfNecessary(value, field.getType(), field);
    }

    private String normalizePropKey(String name) {
        Matcher matcher = CAMEL_CASE_PATTERN.matcher(name);
        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            matcher.appendReplacement(result, matcher.group(1) + '-' + StringUtils.uncapitalize(matcher.group(2)));
        }
        matcher.appendTail(result);
        return result.toString().replaceAll("_", "-").toLowerCase(Locale.ENGLISH);
    }

    @Data
    static class ValueBeanFieldBinder {

        private String expr;

        private WeakReference<Object> beanRef;

        private Field dynamicField;

        private String beanName;

        private Method binder;

        ValueBeanFieldBinder(String expr, Field dynamicField, Object bean, String beanName) {
            this.beanRef = new WeakReference<>(bean);
            this.expr = expr;
            this.dynamicField = dynamicField;
            this.beanName = beanName;
        }
    }
}
