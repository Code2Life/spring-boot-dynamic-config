package com.example.demo;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import top.code2life.config.FeatureGate;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Code2Life
 **/
@RequiredArgsConstructor
@RestController
public class DemoController {


    private final DemoConfigProperties demoConfigProps;

    private final DemoDynamicValue demoDynamicValue;

    private final FeatureGate featureGate;

    @RequestMapping(value = "/demo", method = RequestMethod.GET)
    public Object getDemoConfigProps() {
        Map<String, Object> resp = new HashMap<>(4);
        DemoConfigProperties configurationProps = new DemoConfigProperties();
        BeanUtils.copyProperties(demoConfigProps, configurationProps);
        resp.put("valuePlaceHolder", demoDynamicValue.getDynamicHello());
        resp.put("valueSpringEL", demoDynamicValue.getBetaUserList());
        resp.put("someUserInWhiteList", featureGate.isFeatureEnabled(demoDynamicValue.getBetaUserList(), "user4"));
        resp.put("betaFeatureEnabled", featureGate.isFeatureEnabled("beta.enabled"));
        resp.put("configurationPropertiesObj", configurationProps);
        return resp;
    }
}
