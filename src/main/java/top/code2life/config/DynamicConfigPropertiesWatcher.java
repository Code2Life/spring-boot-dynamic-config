package top.code2life.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.env.PropertySourceLoader;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.support.SpringFactoriesLoader;
import org.springframework.util.StringUtils;

import javax.annotation.PostConstruct;
import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * Enhance PropertySource when spring.config.location is specified, it will start directory-watch,
 * listening any changes on configuration files, then publish ConfigurationChangedEvent.
 *
 * @author Code2Life
 * @see ConfigurationChangedEvent
 */
@Slf4j
@ConditionalOnProperty("spring.config.location")
public class DynamicConfigPropertiesWatcher implements DisposableBean {

    private static final long SYMBOL_LINK_POLLING_INTERVAL = 5000;
    private static final long NORMAL_FILE_POLLING_INTERVAL = 90000;
    private static final String FILE_COLON_SYMBOL = "file:";
    private static final String SYMBOL_LINK_DIR = "..data";
    private static final String WATCH_THREAD = "config-watcher";
    private static final String POLLING_THREAD = "config-watcher-polling";
    private static final String FILE_SOURCE_CONFIGURATION_PATTERN = "^.*Config\\sresource.*file.*$";
    private static final String FILE_SOURCE_CONFIGURATION_PATTERN_LEGACY = "^.+Config:\\s\\[file:.*$";
    private static final Map<String, PropertySourceMeta> PROPERTY_SOURCE_META_MAP = new HashMap<>(8);

    private final StandardEnvironment env;
    private final ApplicationEventPublisher eventPublisher;
    private final List<PropertySourceLoader> propertyLoaders;

    private WatchService watchService;
    private long symbolicLinkModifiedTime = 0;

    @Value("${spring.config.location:}")
    private String configLocation;

    DynamicConfigPropertiesWatcher(StandardEnvironment env, ApplicationEventPublisher eventPublisher) {
        this.env = env;
        this.eventPublisher = eventPublisher;
        this.propertyLoaders = SpringFactoriesLoader.loadFactories(PropertySourceLoader.class,
                getClass().getClassLoader());
    }

    @Override
    public void destroy() {
        closeConfigDirectoryWatch();
    }

    /**
     * Watch config directory after initializing, using WatchService API
     */
    @PostConstruct
    @SuppressWarnings("AlibabaThreadPoolCreation")
    public void watchConfigDirectory() {
        if (!StringUtils.hasText(configLocation)) {
            log.info("no spring.config.location configured, file watch will not start.");
            return;
        }
        MutablePropertySources propertySources = env.getPropertySources();
        for (PropertySource<?> ps : propertySources) {
            boolean isFilePropSource = ps.getName().matches(FILE_SOURCE_CONFIGURATION_PATTERN_LEGACY) || ps.getName().matches(FILE_SOURCE_CONFIGURATION_PATTERN);
            if (isFilePropSource) {
                normalizeAndRecordPropSource(ps);
            }
        }
        Executors.newSingleThreadExecutor(r -> new Thread(r, WATCH_THREAD)).submit(this::startWatchDir);
    }

    /**
     * change config location, only for test usage
     *
     * @param path file path
     */
    void setConfigLocation(String path) {
        this.configLocation = path;
    }

    private void normalizeAndRecordPropSource(PropertySource<?> ps) {
        String name = ps.getName();
        int beginIndex = name.indexOf("[") + 1;
        int endIndex = name.indexOf("]");
        if (beginIndex < 1 && endIndex < 1) {
            log.warn("unrecognized config location, property source name is: {}", name);
        }
        String pathStr = name.substring(beginIndex, endIndex);
        if (pathStr.contains(FILE_COLON_SYMBOL)) {
            pathStr = pathStr.replace(FILE_COLON_SYMBOL, "");
        }
        PROPERTY_SOURCE_META_MAP.put(pathStr.replaceAll("\\\\", "/"), new PropertySourceMeta(ps, Paths.get(pathStr), 0L));
        log.debug("configuration file found: {}", pathStr);
    }

    private void startWatchDir() {
        try {
            log.info("start watching configuration directory: {}", configLocation);
            watchService = FileSystems.getDefault().newWatchService();
            Paths.get(configLocation).register(watchService, StandardWatchEventKinds.ENTRY_MODIFY, StandardWatchEventKinds.ENTRY_CREATE);
            checkChangesWithPeriod();
            WatchKey key;
            while ((key = watchService.take()) != null) {
                // avoid receiving two ENTRY_MODIFY events: file modified and timestamp updated
                Thread.sleep(50);
                for (WatchEvent<?> event : key.pollEvents()) {
                    Path path = (Path) event.context();
                    reloadChangedFile(path, false);

                }
                key.reset();
            }
            log.warn("config directory watch stopped unexpectedly, dynamic configuration won't take effect.");
        } catch (ClosedWatchServiceException cse) {
            log.info("configuration watcher has been stopped.");
        } catch (Exception ex) {
            log.error("failed to watch config directory: ", ex);
        }
    }

    @SuppressWarnings("AlibabaThreadPoolCreation")
    private void checkChangesWithPeriod() throws IOException {
        Path symLinkPath = Paths.get(configLocation, SYMBOL_LINK_DIR);
        boolean hasDotDataLinkFile = new File(configLocation, SYMBOL_LINK_DIR).exists();
        if (hasDotDataLinkFile) {
            log.info("ConfigMap/Secret mode detected, will polling symbolic link instead.");
            symbolicLinkModifiedTime = Files.getLastModifiedTime(symLinkPath, LinkOption.NOFOLLOW_LINKS).toMillis();
            Executors.newSingleThreadScheduledExecutor(r -> new Thread(r, POLLING_THREAD)).scheduleWithFixedDelay(
                    this::checkSymbolicLink, SYMBOL_LINK_POLLING_INTERVAL, SYMBOL_LINK_POLLING_INTERVAL, TimeUnit.MILLISECONDS);
        } else {
            // longer check for all config files, make up mechanism if WatchService doesn't work
            Executors.newSingleThreadScheduledExecutor(r -> new Thread(r, POLLING_THREAD)).scheduleWithFixedDelay(
                    this::reloadAllConfigFiles, NORMAL_FILE_POLLING_INTERVAL, NORMAL_FILE_POLLING_INTERVAL, TimeUnit.MILLISECONDS);
        }
    }

    private void checkSymbolicLink() {
        try {
            Path symLinkPath = Paths.get(configLocation, SYMBOL_LINK_DIR);
            long tmp = Files.getLastModifiedTime(symLinkPath, LinkOption.NOFOLLOW_LINKS).toMillis();
            if (tmp != symbolicLinkModifiedTime) {
                reloadAllConfigFiles(true);
                symbolicLinkModifiedTime = tmp;
            }
        } catch (IOException ex) {
            log.warn("could not check symbolic link of config dir: {}", ex.getMessage());
        }
    }

    private void reloadAllConfigFiles() {
        reloadAllConfigFiles(false);
    }

    private void reloadAllConfigFiles(boolean forceReload) {
        try (Stream<Path> paths = Files.walk(Paths.get(configLocation))) {
            paths.forEach((path) -> {
                if (!Files.isDirectory(path)) {
                    reloadChangedFile(path, forceReload);
                }
            });
        } catch (IOException e) {
            log.warn("can not walk through config directory: {}", e.getMessage());
        }
    }

    private void reloadChangedFile(Path path, boolean forceReload) {
        if (SYMBOL_LINK_DIR.equals(path.toString())) {
            return;
        }
        Path absPath = null;
        try {
            absPath = Paths.get(configLocation, path.toString());
            String absPathStr = absPath.toString();
            // remove ./ or .\ at the beginning of the path
            boolean beginWithRelative = absPathStr.length() > 2 && (absPathStr.startsWith("./") || absPathStr.startsWith(".\\"));
            if (beginWithRelative) {
                absPathStr = absPathStr.substring(2);
            }
            PropertySourceMeta propertySourceMeta = PROPERTY_SOURCE_META_MAP.get(absPathStr.replaceAll("\\\\", "/"));
            if (propertySourceMeta == null) {
                log.debug("changed file at config location is not recognized: {}", absPathStr);
                return;
            }
            long currentModTs = Files.getLastModifiedTime(absPath).toMillis();
            long mdt = propertySourceMeta.getLastModifyTime();
            if (forceReload || mdt != currentModTs) {
                doReloadConfigFile(propertySourceMeta, absPathStr, currentModTs);
            }
        } catch (Exception ex) {
            log.error("reload configuration file {} failed: ", absPath, ex);
        }
    }

    private void doReloadConfigFile(PropertySourceMeta propertySourceMeta, String path, long modifyTime) throws IOException {
        log.info("config file has been changed: {}", path);
        String extension = "";
        int i = path.lastIndexOf('.');
        if (i > 0) {
            extension = path.substring(i + 1);
        }
        String propertySourceName = propertySourceMeta.getPropertySource().getName();
        ConfigurationChangedEvent event = new ConfigurationChangedEvent(path);
        FileSystemResource resource = new FileSystemResource(path);
        for (PropertySourceLoader loader : propertyLoaders) {
            if (Arrays.asList(loader.getFileExtensions()).contains(extension)) {
                // use this loader to load config resource
                List<PropertySource<?>> newPropsList = loader.load(propertySourceName, resource);
                if (newPropsList.size() >= 1) {
                    PropertySource<?> newProps = newPropsList.get(0);
                    event.setPrevious(env.getPropertySources().get(propertySourceName));
                    event.setCurrent(newProps);
                    env.getPropertySources().replace(propertySourceName, newProps);
                    propertySourceMeta.setLastModifyTime(modifyTime);
                    eventPublisher.publishEvent(event);
                }
                break;
            }
        }
    }

    private void closeConfigDirectoryWatch() {
        if (watchService != null) {
            try {
                watchService.close();
                log.info("config properties watcher bean is destroying, WatchService stopped.");
            } catch (IOException e) {
                log.warn("can not close config directory watcher. ", e);
            }
        }
    }
}
