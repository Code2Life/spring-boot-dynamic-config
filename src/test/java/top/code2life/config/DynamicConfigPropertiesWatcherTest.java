package top.code2life.config;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.env.StandardEnvironment;

import java.io.File;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.LinkOption;

import static top.code2life.config.DynamicConfigPropertiesWatcher.WATCHABLE_TARGETS;
import static top.code2life.config.DynamicConfigTests.CONFIG_LOCATION;

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
    public void testSymbolLinkWatch() throws Exception {
        FileSystemWatchTarget watchTarget = new FileSystemWatchTarget(FileSystemWatchTarget.WatchTargetType.CONFIG_LOCATION, CONFIG_LOCATION);
        WATCHABLE_TARGETS.put(watchTarget.getNormalizedDir(), watchTarget);
        DynamicConfigPropertiesWatcher watcher = new DynamicConfigPropertiesWatcher(environment, eventPublisher);
        File file = new File(CONFIG_LOCATION, "..data");
        try (PrintWriter writer = new PrintWriter(file)) {
            writer.write("test-symbolic-link");
            watcher.watchConfigDirectory();
        }
        Thread.sleep(1000);
        long mdt1 = Files.getLastModifiedTime(file.toPath(), LinkOption.NOFOLLOW_LINKS).toMillis();
        try (PrintWriter writer = new PrintWriter(file)) {
            writer.write("test-symbolic-link-2");
        }
        long mdt2 = Files.getLastModifiedTime(file.toPath(), LinkOption.NOFOLLOW_LINKS).toMillis();
        Assertions.assertTrue(mdt1 != mdt2);
        Thread.sleep(7000);
    }

    @Test
    public void testWatchUnRecognizedFile() throws Exception {
        FileSystemWatchTarget watchTarget = new FileSystemWatchTarget(FileSystemWatchTarget.WatchTargetType.CONFIG_LOCATION, CONFIG_LOCATION);
        WATCHABLE_TARGETS.put(watchTarget.getNormalizedDir(), watchTarget);
        DynamicConfigPropertiesWatcher watcher = new DynamicConfigPropertiesWatcher(environment, eventPublisher);
        // watch nothing since config.location not set
        watcher.watchConfigDirectory();
        File file = new File(CONFIG_LOCATION, "unknown.txt");
        try (PrintWriter writer = new PrintWriter(file)) {
            writer.write("test-data");
        }
        Thread.sleep(500);
        // should not throw any exception
    }

    @Test
    public void testFileWatch() {
        DynamicConfigPropertiesWatcher watcher = new DynamicConfigPropertiesWatcher(environment, eventPublisher);
        watcher.destroy();
        // should not throw any exception
    }
}
