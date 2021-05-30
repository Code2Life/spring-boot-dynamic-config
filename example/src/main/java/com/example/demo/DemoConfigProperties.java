package com.example.demo;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import top.code2life.config.DynamicConfig;

import java.util.List;
import java.util.Map;

/**
 * @author Code2Life
 **/
@Data
@DynamicConfig
@ConfigurationProperties(prefix = "dynamic.prop")
@Configuration
public class DemoConfigProperties {

    private String str;

    private Map<String, String> mapVal;

    private List<Integer> listVal;

    private List<DemoConfigProperties> nestedList;

    private Map<String, DemoConfigProperties> nestedMap;

    private DemoConfigProperties nested;
}
