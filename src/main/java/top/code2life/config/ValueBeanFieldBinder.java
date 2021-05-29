package top.code2life.config;


import lombok.Data;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

@Data
class ValueBeanFieldBinder {

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