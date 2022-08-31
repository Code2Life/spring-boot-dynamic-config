package top.code2life.config.sample.configtree;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import top.code2life.config.DynamicConfig;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author Code2Life
 **/
@Data
@Configuration
@DynamicConfig
@ConfigurationProperties(prefix = "module-b.abc.my-prop")
public class TestConfTreeConfigurationProperties {

    private String str;
    private Double doubleVal;
    private int intVal;
    private Integer boxedIntVal;
    private ConcurrentHashMap<String, Object> mapVal;
    private List<String> listVal;
    private List<TestConfTreeConfigurationProperties> listObj;
    private Nested nested;

    @Data
    public static class Nested {

        private Map<String, String> mapVal;

        private List<String> collectionVal;
    }
}
