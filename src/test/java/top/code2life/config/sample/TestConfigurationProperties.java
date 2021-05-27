package top.code2life.config.sample;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import top.code2life.config.DynamicConfig;

import java.util.Map;

/**
 * @author Code2Life
 **/
@Data
@DynamicConfig
@Configuration
@ConfigurationProperties(prefix = "my-prop")
public class TestConfigurationProperties {

    private String str;
    private Double doubleVal;
    private Map<String, Object> mapVal;
}
