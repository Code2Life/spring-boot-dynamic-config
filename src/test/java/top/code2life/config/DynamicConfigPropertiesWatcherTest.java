package top.code2life.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.env.StandardEnvironment;

import java.io.File;
import java.io.PrintWriter;

/**
 * @author Code2Life
 **/
@SpringBootTest(classes = {DynamicConfigPropertiesWatcher.class})
public class DynamicConfigPropertiesWatcherTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private StandardEnvironment environment;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Test
    public void testBeanLoaded() throws Exception {
        try {
            applicationContext.getBean(DynamicConfigPropertiesWatcher.class);
            throw new Exception("bean should not be loaded");
        } catch (BeansException ex) {
            // should throw this exception, because of no --spring.config.location configured
        }
    }

    @Test
    public void testFileWatch() {
        DynamicConfigPropertiesWatcher watcher = new DynamicConfigPropertiesWatcher(environment, eventPublisher);
        watcher.destroy();
        // should not throw any exception
    }

    @Test
    public void testWatchUnRecognizedFile() throws Exception {
        DynamicConfigPropertiesWatcher watcher = new DynamicConfigPropertiesWatcher(environment, eventPublisher);
        // watch nothing since config.location not set
        watcher.watchConfigDirectory();
        watcher.setConfigLocation(DynamicConfigTests.CONFIG_LOCATION);
        watcher.watchConfigDirectory();
        File file = new File(DynamicConfigTests.CONFIG_LOCATION, "unknown.txt");
        try (PrintWriter writer = new PrintWriter(file)) {
            writer.write("test-data");
        }
        Thread.sleep(500);
        // should not throw any exception
    }
}
