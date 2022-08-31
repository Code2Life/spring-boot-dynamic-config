package top.code2life.config.sample.configtree;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author Code2Life
 **/
@Configuration
public class TestConfigTreeBeanConfiguration {


    @Autowired
    private TestConfTreeConfigurationProperties testProperty;

    @Bean
    public TestBean testConfigTreeBean() {
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
