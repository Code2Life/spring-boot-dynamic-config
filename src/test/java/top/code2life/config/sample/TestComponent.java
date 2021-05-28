package top.code2life.config.sample;

import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;
import top.code2life.config.DynamicConfig;

import java.util.Set;

/**
 * @author Code2Life
 **/
@Data
@DynamicConfig
@Component
public class TestComponent {

    @Value("${dynamic-test-plain:default}")
    private String plainValue;

    @Value("#{@featureGate.convert('${dynamic-feature-conf}')}")
    private Set<String> someBetaFeatureConfig;

    @Value("#{@testComponent.transform(${dynamic.transform-a:20}, ${dynamic.transform-b:10})} ")
    private double transformBySpEL;


    public double transform(double t1, double t2) {
        return t1 / t2;
    }
}
