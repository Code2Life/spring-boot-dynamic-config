package top.code2life.config.sample.configtree;

import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import top.code2life.config.DynamicConfig;

import java.util.Set;

/**
 * @author Code2Life
 **/
@Data
@DynamicConfig
@Component
public class TestConfigTreeComponent {

    @Value("${module-a.xyz.dynamic-test-plain:default}")
    private String plainValue;

    @Value("#{@featureGate.convert('${module-a.xyz.dynamic-feature-conf:}')}")
    private Set<String> someBetaFeatureConfig;

    @Value("#{T(top.code2life.config.sample.configtree.TestConfigTreeComponent).transform(${module-a.xyz.dynamic.transform-a:20}, ${module-a.xyz.dynamic.transform-b:10})} ")
    private double transformBySpEL;


    public static double transform(double t1, double t2) {
        return t1 / t2;
    }
}
