package top.code2life.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.TypeConverter;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.config.BeanExpressionContext;
import org.springframework.beans.factory.config.BeanExpressionResolver;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.context.properties.ConfigurationPropertiesBindingPostProcessor;
import org.springframework.boot.origin.OriginTrackedValue;
import org.springframework.context.ApplicationContext;
import org.springframework.context.event.EventListener;
import org.springframework.util.StringUtils;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static top.code2life.config.ConfigurationUtils.*;
import static top.code2life.config.DynamicConfigBeanPostProcessor.DYNAMIC_FIELD_BINDER_MAP;

/**
 * @author Code2Life
 **/
@Slf4j
@ConditionalOnBean(DynamicConfigPropertiesWatcher.class)
public class ConfigurationChangedEventHandler {

    private static final String DOT_SYMBOL = ".";
    private static final String INDEXED_PROP_PATTERN = "\\[\\d{1,3}]";

    private final BeanExpressionResolver exprResolver;
    private final BeanExpressionContext exprContext;
    private final ConfigurationPropertiesBindingPostProcessor processor;
    private final ConfigurableListableBeanFactory beanFactory;

    ConfigurationChangedEventHandler(ApplicationContext applicationContext, BeanFactory beanFactory) {
        if (!(beanFactory instanceof ConfigurableListableBeanFactory)) {
            throw new IllegalArgumentException(
                    "DynamicConfig requires a ConfigurableListableBeanFactory");
        }
        ConfigurableListableBeanFactory factory = (ConfigurableListableBeanFactory) beanFactory;
        this.beanFactory = factory;
        this.processor = applicationContext.getBean(ConfigurationPropertiesBindingPostProcessor.class);
        this.exprResolver = (factory).getBeanExpressionResolver();
        this.exprContext = new BeanExpressionContext(factory, null);
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
            rebindRelatedConfigurationPropsBeans(diff, toRefreshProps);
            log.info("config changes of {} have been processed", event.getSource());
        } catch (Exception ex) {
            log.warn("config changes of {} can not be processed, error:", event.getSource(), ex);
        }
    }

    /**
     * loop current properties and prev properties, find diff
     * removed properties won't impact existing bean values
     */
    private Map<Object, Object> getPropertyDiff(Map<Object, OriginTrackedValue> prev, Map<Object, OriginTrackedValue> current) {
        Map<Object, Object> diff = new HashMap<>(4);
        filterAddOrUpdatedKeys(prev, current, diff);
        filterMissingKeys(prev, current, diff);
        return diff;
    }

    private void filterAddOrUpdatedKeys(Map<Object, OriginTrackedValue> prev, Map<Object, OriginTrackedValue> current, Map<Object, Object> diff) {
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
    }

    private void filterMissingKeys(Map<Object, OriginTrackedValue> prev, Map<Object, OriginTrackedValue> current, Map<Object, Object> diff) {
        for (Map.Entry<Object, OriginTrackedValue> entry : prev.entrySet()) {
            Object k = entry.getKey();
            if (!current.containsKey(k)) {
                diff.put(k, null);
                log.debug("found deleted k of dynamic config: {}", k);
            }
        }
    }

    private void processConfigPropsClass(Map<String, ValueBeanFieldBinder> result, String key) {
        DynamicConfigBeanPostProcessor.DYNAMIC_CONFIG_PROPS_BINDER_MAP.forEach((prefix, binder) -> {
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
            convertAndBindFieldValue(val, binder, bean);
        }
    }

    private void convertAndBindFieldValue(Object val, ValueBeanFieldBinder binder, Object bean) throws IllegalAccessException {
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

    private void rebindRelatedConfigurationPropsBeans(Map<Object, Object> diff, Map<String, ValueBeanFieldBinder> toRefreshProps) throws IllegalAccessException {
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
            String normalizedFieldPath = findParentPath(prefix, rawKey);
            String leafKey = rawKey.substring(rawKey.lastIndexOf(DOT_SYMBOL) + 1);
            removeMissingMapKeyIfMatch(getTargetClassOfBean(rootBean), rootBean, normalizedFieldPath, leafKey);
        }
    }

    private void removeMissingMapKeyIfMatch(Class<?> clazz, Object obj, String path, String mapKey) throws IllegalAccessException {
        int pos = path.indexOf(DOT_SYMBOL);
        boolean onLeaf = pos == -1;
        Field[] fields = clazz.getDeclaredFields();
        for (Field f : fields) {
            if (isIgnorableField(f)) {
                continue;
            }
            String fieldName = f.getName();
            boolean matchObjPath = StringUtils.startsWithIgnoreCase(path, normalizePropKey(fieldName));
            if (matchObjPath && onLeaf && Map.class.isAssignableFrom(f.getType())) {
                f.setAccessible(true);
                ((Map<?, ?>) f.get(obj)).remove(mapKey);
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

    private boolean isIgnorableField(Field f) {
        int modifiers = f.getModifiers();
        Class<?> type = f.getType();
        return Modifier.isStatic(modifiers) || Modifier.isFinal(modifiers) || BeanUtils.isSimpleValueType(type);
    }

    private String findParentPath(String prefix, String rawKey) {
        String normalizedFieldPath = normalizePropKey(rawKey).substring(prefix.length() + 1);
        int pathPos = normalizedFieldPath.lastIndexOf(DOT_SYMBOL);
        if (pathPos != -1) {
            normalizedFieldPath = normalizedFieldPath.substring(0, pathPos);
        } else {
            normalizedFieldPath = "";
        }
        return normalizedFieldPath;
    }

    private Object convertIfNecessary(Field field, Object value) {
        TypeConverter converter = beanFactory.getTypeConverter();
        return converter.convertIfNecessary(value, field.getType(), field);
    }
}
