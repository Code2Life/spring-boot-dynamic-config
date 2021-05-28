package com.example.demo;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import top.code2life.config.DynamicConfig;

/**
 * @author Code2Life
 **/
@Getter
@DynamicConfig
@Configuration
public class DemoDynamicValue {

    @Value("${dynamic.hello-world}")
    private String dynamicHello;
}
