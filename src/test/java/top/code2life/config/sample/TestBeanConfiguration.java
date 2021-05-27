package top.code2life.config.sample;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author Code2Life
 **/
@Configuration
public class TestBeanConfiguration {


    @Autowired
    private TestConfigurationProperties testProperty;

    @Bean
    public TestBean testBean() {
        return new TestBean(testProperty.getStr());
    }

    public String getCurrentStrValue() {
        return testProperty.getStr();
    }

    @Data
    @AllArgsConstructor
    public static class TestBean {
        private String internalStr;
    }
}
