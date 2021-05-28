package com.example.demo;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import top.code2life.config.DynamicConfig;

/**
 * @author Code2Life
 **/
@Data
@DynamicConfig
@ConfigurationProperties(prefix = "dynamic.prop")
@Configuration
public class DemoConfigProperties {

    private String str;

}
