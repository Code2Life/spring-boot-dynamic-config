package com.example.demo;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import top.code2life.config.DynamicConfig;

import java.util.List;
import java.util.Map;

/**
 * @author Code2Life
 **/
@Data
@DynamicConfig
@ConfigurationProperties(prefix = "module-a.abc.prop")
@Configuration
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DemoConfigTreeProperties {

    private String str;

    private Map<String, String> mapVal;

    private List<Integer> listVal;

    private Map<String, DemoConfigTreeProperties> nestedMap;

    private DemoConfigTreeProperties nested;
}
