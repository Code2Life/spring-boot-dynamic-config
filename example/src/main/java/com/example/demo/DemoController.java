package com.example.demo;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import top.code2life.config.DynamicConfig;
import top.code2life.config.FeatureGate;

import java.util.Set;

/**
 * @author Code2Life
 **/
@RequiredArgsConstructor
@RestController
public class DemoController {


    @DynamicConfig
    @Value("#{@featureGate.convert('${some.feature.beta-list}')}")
    private Set<String> betaUserList;

    private final DemoConfigProperties demoConfigProps;

    private final DemoDynamicValue demoDynamicValue;

    private final FeatureGate featureGate;

    @RequestMapping(value = "/demo", method = RequestMethod.GET)
    public Object getDemoConfigProps() {
        DemoConfigProperties resp = new DemoConfigProperties();
        resp.setStr(demoConfigProps.getStr());
        resp.setListVal(demoConfigProps.getListVal());
        resp.setMapVal(demoConfigProps.getMapVal());
        resp.setNestedList(demoConfigProps.getNestedList());
        resp.setNestedMap(demoConfigProps.getNestedMap());
        resp.setNested(demoConfigProps.getNested());
        return resp;
    }

    @RequestMapping(value = "/demo2", method = RequestMethod.GET)
    public String getDemoConfig2() {
        return demoDynamicValue.getDynamicHello();
    }

    @RequestMapping(value = "/demo3", method = RequestMethod.GET)
    public boolean isUserInBetaList(@RequestParam String id) {
        return featureGate.isFeatureEnabled(betaUserList, id);
    }
}
