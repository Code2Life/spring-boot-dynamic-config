package top.code2life.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.BeanUtils;
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
import org.springframework.util.ClassUtils;
import org.springframework.util.StringUtils;

import javax.annotation.PostConstruct;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
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
@ConditionalOnBean(DynamicConfigPropertiesWatcher.class)
public class DynamicConfigBeanPostProcessor implements BeanPostProcessor {

    private static final String VALUE_EXPR_PREFIX = "$";
    private static final String SP_EL_PREFIX = "#";
    private static final String DOT_SYMBOL = ".";

    private static final Pattern VALUE_PATTERN = Pattern.compile("\\$\\{([^:}]+):?([^}]*)}");
    private static final Pattern CAMEL_CASE_PATTERN = Pattern.compile("([^A-Z-])([A-Z])");
    public static final String INDEXED_PROP_PATTERN = "\\[\\d{1,3}\\]";

    private static final Map<String, List<ValueBeanFieldBinder>> DYNAMIC_FIELD_BINDER_MAP = new ConcurrentHashMap<>(16);
    private static final Map<String, ValueBeanFieldBinder> DYNAMIC_CONFIG_PROPS_BINDER_MAP = new ConcurrentHashMap<>(8);
    private static final Map<String, Object> DYNAMIC_BEAN_MAP = new ConcurrentHashMap<>(16);

    private final BeanExpressionResolver exprResolver;
    private final BeanExpressionContext exprContext;
    private final ConfigurableListableBeanFactory beanFactory;
    private final ApplicationContext applicationContext;
    private final ConfigurationPropertiesBindingPostProcessor processor;

    DynamicConfigBeanPostProcessor(ApplicationContext applicationContext, BeanFactory beanFactory) {
        if (!(beanFactory instanceof ConfigurableListableBeanFactory)) {
            throw new IllegalArgumentException(
                    "DynamicConfig requires a ConfigurableListableBeanFactory");
        }
        this.processor = applicationContext.getBean(ConfigurationPropertiesBindingPostProcessor.class);
        this.applicationContext = applicationContext;
        this.beanFactory = (ConfigurableListableBeanFactory) beanFactory;
        this.exprResolver = ((ConfigurableListableBeanFactory) beanFactory).getBeanExpressionResolver();
        this.exprContext = new BeanExpressionContext((ConfigurableListableBeanFactory) beanFactory, null);
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

    /**
     * Listen config changed event, to process related beans and set latest values for their fields
     *
     * @param event ConfigurationChangedEvent indicates a configuration file changed event
     */
    @EventListener
    @SuppressWarnings("unchecked")
    public synchronized void handleEvent(ConfigurationChangedEvent event) {
        try {
            Map<Object, Object> diff = getPropertyDiff((Map<Object, OriginTrackedValue>) event.getPrevious().getSource(), (Map<Object, OriginTrackedValue>) event.getCurrent().getSource());
            Map<String, ValueBeanFieldBinder> toRefreshProps = new HashMap<>(4);
            for (Map.Entry<Object, Object> entry : diff.entrySet()) {
                String key = entry.getKey().toString();
                processConfigPropsClass(toRefreshProps, key);
                processValueField(key, entry.getValue());
            }
            for (Map.Entry<String, ValueBeanFieldBinder> entry : toRefreshProps.entrySet()) {
                String beanName = entry.getKey();
                ValueBeanFieldBinder binder = entry.getValue();
                Object bean = binder.getBeanRef().get();
                if (bean != null) {
                    processor.postProcessBeforeInitialization(bean, beanName);
                    // AggregateBinder - MapBinder will merge properties while binding
                    // need to check deleted keys and remove from map fields
                    removeMissingPropsMapFields(diff, bean, binder.getExpr());
                    log.debug("changes detected, re-bind ConfigurationProperties bean: {}", beanName);
                }
            }
            log.info("config changes of {} have been processed", event.getSource());
        } catch (Exception ex) {
            log.warn("config changes of {} can not be processed, error:", event.getSource(), ex);
            if (log.isDebugEnabled()) {
                log.error("error detail is:", ex);
            }
        }
    }

    ///region collection bean information after initialization

    private void handleDynamicBean(Object bean, String beanName) {
        // avoid duplicate processing after post processor
        if (DYNAMIC_BEAN_MAP.containsKey(beanName)) {
            return;
        }
        DYNAMIC_BEAN_MAP.putIfAbsent(beanName, bean);
        Class<?> clazz = getTargetClassOfBean(bean);
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
        prefix = normalizePropKey(prefix);
        ValueBeanFieldBinder binder = new ValueBeanFieldBinder(prefix, null, bean, beanName);
        DYNAMIC_CONFIG_PROPS_BINDER_MAP.putIfAbsent(prefix, binder);
    }

    private void collectionValueAnnotationMetadata(Object bean, String beanName, Class<?> clazz, Field field) {
        String valueExpr = field.getAnnotation(Value.class).value();
        if (!valueExpr.startsWith(VALUE_EXPR_PREFIX) && !valueExpr.startsWith(SP_EL_PREFIX)) {
            return;
        }
        List<String> propKeyList = extractValueFromExpr(valueExpr);
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

    ///endregion


    ///region handle configuration changed event, manipulate fields and re-bind properties

    /**
     * loop current properties and prev properties, find diff
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
                    log.debug("found changed key of dynamic config: {}", k);
                }
            } else {
                diff.put(k, v.getValue());
                log.debug("found new added key of dynamic config: {}", k);
            }
        }
        for (Map.Entry<Object, OriginTrackedValue> entry : prev.entrySet()) {
            Object k = entry.getKey();
            if (!current.containsKey(k)) {
                diff.put(k, null);
                log.debug("found deleted k of dynamic config: {}", k);
            }
        }
        return diff;
    }

    private void processConfigPropsClass(Map<String, ValueBeanFieldBinder> result, String key) {
        DYNAMIC_CONFIG_PROPS_BINDER_MAP.forEach((prefix, binder) -> {
            if (StringUtils.startsWithIgnoreCase(normalizePropKey(key), prefix)) {
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
            if (bean == null) {
                continue;
            }
            Field field = binder.getDynamicField();
            field.setAccessible(true);
            String expr = binder.getExpr();
            String newExpr = beanFactory.resolveEmbeddedValue(expr);
            if (expr.startsWith(SP_EL_PREFIX)) {
                Object evaluatedVal = exprResolver.evaluate(newExpr, exprContext);
                field.set(bean, convertIfNecessary(field, evaluatedVal));
            } else {
                field.set(bean, convertIfNecessary(field, val));
            }
            if (log.isDebugEnabled()) {
                log.debug("dynamic config found, set field: '{}' of class: '{}' with new value", field.getName(), bean.getClass().getSimpleName());
            }

        }
    }

    private void removeMissingPropsMapFields(Map<Object, Object> diff, Object rootBean, String prefix) throws IllegalAccessException {
        for (Map.Entry<Object, Object> entry : diff.entrySet()) {
            Object propKey = entry.getKey();
            Object value = entry.getValue();
            if (value != null) {
                // only null value prop need to be removed from field value
                continue;
            }
            String rawKey = propKey.toString();
            // 'a.b[1].c.d' liked changes would be refreshed wholly, no need to handle
            if (rawKey.matches(INDEXED_PROP_PATTERN)) {
                continue;
            }

            // if key 'a.b.c.d' is removed, need to check if 'a.b.c' is a map, if so, remove map key 'd'
            String normalizedFieldPath = normalizePropKey(rawKey).substring(prefix.length() + 1);
            int pathPos = normalizedFieldPath.lastIndexOf(DOT_SYMBOL);
            if (pathPos != -1) {
                normalizedFieldPath = normalizedFieldPath.substring(0, pathPos);
            } else {
                normalizedFieldPath = "";
            }
            removeMissingMapKeyIfMatch(getTargetClassOfBean(rootBean), rootBean, normalizedFieldPath, rawKey.substring(rawKey.lastIndexOf(DOT_SYMBOL) + 1));
        }
    }

    private void removeMissingMapKeyIfMatch(Class<?> clazz, Object obj, String path, String mapKey) throws IllegalAccessException {
        int pos = path.indexOf(DOT_SYMBOL);
        boolean onLeaf = pos == -1;
        Field[] fields = clazz.getDeclaredFields();
        for (Field f : fields) {
            int modifiers = f.getModifiers();
            Class<?> type = f.getType();
            boolean needIgnore = Modifier.isStatic(modifiers) || Modifier.isFinal(modifiers) || BeanUtils.isSimpleValueType(type);
            if (needIgnore) {
                continue;
            }
            String fieldName = f.getName();
            // CGlib fields, ignore
            if (fieldName.contains(VALUE_EXPR_PREFIX)) {
                continue;
            }
            boolean matchObjPath = StringUtils.startsWithIgnoreCase(path, normalizePropKey(fieldName));
            if (matchObjPath && onLeaf && Map.class.isAssignableFrom(type)) {
                f.setAccessible(true);
                Map<?, ?> mapLikeField = (Map<?, ?>) f.get(obj);
                mapLikeField.remove(mapKey);
                log.info("key {} has been removed from {} because of configuration change.", mapKey, path);
                break;
            }
            // dive to next level for case: path: a.b.c, field: b
            if (matchObjPath && !onLeaf) {
                f.setAccessible(true);
                Object subObj = f.get(obj);
                removeMissingMapKeyIfMatch(subObj.getClass(), subObj, path.substring(pos + 1), mapKey);
            }
        }
    }

    ///endregion

    ///region tool functions

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

    /**
     * Convert camelCase or snake_case key into kebab-case
     *
     * @param name the key name
     * @return normalized key name
     */
    private String normalizePropKey(String name) {
        if (!StringUtils.hasText(name)) {
            return name;
        }
        Matcher matcher = CAMEL_CASE_PATTERN.matcher(name);
        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            matcher.appendReplacement(result, matcher.group(1) + '-' + StringUtils.uncapitalize(matcher.group(2)));
        }
        matcher.appendTail(result);
        return result.toString().replaceAll("_", "-").toLowerCase(Locale.ENGLISH);
    }

    private Class<?> getTargetClassOfBean(Object bean) {
        Class<?> clazz = AopUtils.getTargetClass(bean);
        if (clazz.getName().contains(ClassUtils.CGLIB_CLASS_SEPARATOR)) {
            clazz = clazz.getSuperclass();
        }
        return clazz;
    }

    ///endregion
}
