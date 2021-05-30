package com.example.demo;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import top.code2life.config.DynamicConfig;

import java.util.Set;

/**
 * @author Code2Life
 **/
@Getter
@DynamicConfig
@Component
public class DemoDynamicValue {

    @Value("${dynamic.hello-world}")
    private String dynamicHello;

    @Value("#{@featureGate.convert('${some.feature.beta-list}')}")
    private Set<String> betaUserList;
}
