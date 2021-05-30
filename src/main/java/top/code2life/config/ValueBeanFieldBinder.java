package top.code2life.config;


import lombok.Data;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;

@Data
class ValueBeanFieldBinder {

    /**
     * Value placeholder / Value SpringEL Expression / ConfigurationProperties annotation prefix
     */
    private String expr;

    /**
     * Reference of the Dynamic Bean instance
     */
    private WeakReference<Object> beanRef;

    /**
     * Record the bound field, only for {@literal @}Value fields binding case
     */
    private Field dynamicField;

    /**
     * name of the Spring bean
     */
    private String beanName;

    ValueBeanFieldBinder(String expr, Field dynamicField, Object bean, String beanName) {
        this.beanRef = new WeakReference<>(bean);
        this.expr = expr;
        this.dynamicField = dynamicField;
        this.beanName = beanName;
    }
}