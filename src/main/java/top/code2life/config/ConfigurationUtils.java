package top.code2life.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.support.AopUtils;
import org.springframework.boot.env.OriginTrackedMapPropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.util.ClassUtils;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static top.code2life.config.DynamicConfigPropertiesWatcher.WATCHABLE_TARGETS;

/**
 * @author Code2Life
 **/
@Slf4j
public class ConfigurationUtils {

    static final String VALUE_EXPR_PREFIX = "$";
    static final String SP_EL_PREFIX = "#";
    static final String OPTIONAL_PREFIX = "optional:";
    static final String CONFIG_TREE_PREFIX = "configtree:";
    static final String CONFIG_FILE_PREFIX = "file:";

    private static final int MAX_DEPTH = 3;
    private static final Pattern VALUE_PATTERN = Pattern.compile("\\$\\{([^:}]+):?([^}]*)}");
    private static final Pattern CAMEL_CASE_PATTERN = Pattern.compile("([^A-Z-])([A-Z])");
    private static final Set<String> VALID_EXTENSION = new HashSet<String>() {
        {
            add("xml");
            add("yml");
            add("yaml");
            add("properties");
        }
    };
    private static final Map<Path, List<Path>> CONFIG_TREE_CACHE = new HashMap<>(4);

    static List<String> extractValueFromExpr(String valueExpr) {
        List<String> keys = new ArrayList<>(2);
        Matcher matcher = VALUE_PATTERN.matcher(valueExpr);
        while (matcher.find()) {
            try {
                // normalized into kebab case (abc-def.g-h)
                keys.add(normalizePropKey(matcher.group(1).trim()));
            } catch (Exception ex) {
                log.warn("can not extract target property from @Value declaration, expr: {}. error: {}", valueExpr, ex.getMessage());
            }
        }
        return keys;
    }

    /**
     * Convert camelCase or snake_case key into kebab-case
     *
     * @param name the key name
     * @return normalized key name
     */
    static String normalizePropKey(String name) {
        return toKebabCase(name);
    }

    static Class<?> getTargetClassOfBean(Object bean) {
        Class<?> clazz = AopUtils.getTargetClass(bean);
        if (clazz.getName().contains(ClassUtils.CGLIB_CLASS_SEPARATOR)) {
            clazz = clazz.getSuperclass();
        }
        return clazz;
    }

    static String normalizePath(String path, String expectedBaseDir) {
        path = trimRelativePathAndReplaceBackSlash(path);
        expectedBaseDir = trimRelativePathAndReplaceBackSlash(expectedBaseDir);
        if (!path.startsWith(expectedBaseDir)) {
            String combined = Paths.get(expectedBaseDir, path).toString();
            return trimRelativePathAndReplaceBackSlash(combined);
        }
        return path;
    }

    static String trimRelativePathAndReplaceBackSlash(String str) {
        if (!StringUtils.hasText(str)) {
            throw new IllegalArgumentException("wrong parameters when processing path");
        }
        boolean beginWithRelative = str.length() > 2 && (str.startsWith("./") || str.startsWith(".\\"));
        if (beginWithRelative) {
            return str.substring(2).replaceAll("\\\\", "/");
        }
        return str.replaceAll("\\\\", "/");
    }


    static String getFileExtension(String path) {
        String extension = "";
        int i = path.lastIndexOf('.');
        if (i > 0) {
            extension = path.substring(i + 1);
        }
        return extension;
    }

    /**
     * Called in ConditionalOnExpression, as bean creation condition
     *
     * @param configLocation spring.config.location value
     * @param configImport   spring.config.import value
     * @return need watch config directory or not
     * @see DynamicConfigPropertiesWatcher
     */
    public static boolean hasWatchableConf(String configLocation, String configImport) {
        if (WATCHABLE_TARGETS.size() > 0) {
            return true;
        }
        getNeedWatchDirAndPath(configLocation, configImport);
        return WATCHABLE_TARGETS.size() > 0;
    }

    private static void getNeedWatchDirAndPath(String configLocation, String configImport) {
        if (StringUtils.hasText(configLocation)) {
            List<String> targets = splitCommaAndFilter(configLocation, s -> StringUtils.hasText(s) && !s.startsWith("classpath:"));
            for (String target : targets) {
                FileSystemWatchTarget watchTarget = new FileSystemWatchTarget(FileSystemWatchTarget.WatchTargetType.CONFIG_LOCATION, target);
                WATCHABLE_TARGETS.put(watchTarget.getNormalizedDir(), watchTarget);
            }
        }

        if (StringUtils.hasText(configImport)) {
            List<String> targets = splitCommaAndFilter(configImport, StringUtils::hasText);
            for (String target : targets) {
                boolean isOptional = target.startsWith(OPTIONAL_PREFIX);
                if (isOptional) {
                    target = target.substring(OPTIONAL_PREFIX.length());
                }
                boolean isImportConfTree = target.startsWith(CONFIG_TREE_PREFIX);
                if (isImportConfTree) {
                    target = target.substring(CONFIG_TREE_PREFIX.length());
                    if (!Files.exists(Paths.get(target))) {
                        log.warn("config-tree import directory not exists, skip dynamic config watch: {}", target);
                        continue;
                    }
                } else {
                    target = target.startsWith(CONFIG_FILE_PREFIX) ? target.substring(CONFIG_FILE_PREFIX.length()) : target;
                    if (!Files.exists(Paths.get(target))) {
                        log.warn("'config import not exists as file, skip dynamic config watch: {}", target);
                        continue;
                    }
                }

                if (isImportConfTree) {
                    // find all available paths of property source files
                    List<Path> configFilePaths = findAllPropertyFilesInTree(Paths.get(target));
                    if (configFilePaths.size() == 0) {
                        log.info("no property sources found in config tree: {}, skip dynamic config file watch", target);
                    } else {
                        // group the to directory, then create multiple watch targets
                        Map<Path, List<Path>> groupedPaths = configFilePaths.stream().collect(Collectors.groupingBy(Path::getParent));
                        for (Map.Entry<Path, List<Path>> entry : groupedPaths.entrySet()) {
                            appendToWatchableTargets(FileSystemWatchTarget.WatchTargetType.CONFIG_IMPORT_TREE, entry.getKey().toString(), entry.getValue(), Paths.get(target));
                        }
                    }
                } else {
                    appendToWatchableTargets(FileSystemWatchTarget.WatchTargetType.CONFIG_IMPORT_FILE, target);
                }
            }
        }
    }

    static List<String> splitCommaAndFilter(String str, Predicate<String> predicate) {
        if (!StringUtils.hasText(str)) {
            return Collections.emptyList();
        }
        return Arrays.stream(str.split(","))
                .map(String::trim).filter(predicate)
                .collect(Collectors.toList());
    }


    static Map<String, Path> findAllKeyAndFilesInConfigTree(Path sourceDirectory) {
        Map<String, Path> propKeyPathMap = new HashMap<>(4);
        findAllPropertyFilesInTree(sourceDirectory).forEach((path) -> {
            String prefix = getPropertyPrefix(sourceDirectory, path);
            if (StringUtils.hasText(prefix)) {
                propKeyPathMap.put(prefix, path);
            }
        });
        return propKeyPathMap;
    }

    static List<Path> findAllPropertyFilesInTree(Path sourceDirectory) {
        List<Path> configTreePaths = new ArrayList<>();
        if (!CONFIG_TREE_CACHE.containsKey(sourceDirectory)) {
            try {
                configTreePaths = Files.find(sourceDirectory, MAX_DEPTH, ConfigurationUtils::isPropertyFile, FileVisitOption.FOLLOW_LINKS).collect(Collectors.toList());
            } catch (IOException e) {
                log.error("can not find property sources for dynamic config: {}", sourceDirectory, e);
            }
            CONFIG_TREE_CACHE.put(sourceDirectory, configTreePaths);
        } else {
            configTreePaths = CONFIG_TREE_CACHE.get(sourceDirectory);
        }
        return configTreePaths;
    }

    static PropertySource<?> addConfigPropPrefix(OriginTrackedMapPropertySource prop, String prefix) {
        Map<String, Object> src = prop.getSource();
        Map<String, Object> map = new HashMap<>(src.size());
        src.forEach((key, val) -> {
            map.put(prefix + '.' + key, val);
        });
        return new OriginTrackedMapPropertySource(prop.getName(), map, true);
    }

    static boolean isPropertyFile(Path path, BasicFileAttributes attributes) {
        String extension = ConfigurationUtils.getFileExtension(path.toString());
        return !hasHiddenPathElement(path) &&
                (attributes.isRegularFile() || attributes.isSymbolicLink()) &&
                VALID_EXTENSION.contains(extension);
    }

    static String getPropertyPrefix(Path rootDirPath, Path fullPath) {
        Path relativePath = rootDirPath.relativize(fullPath);
        int nameCount = relativePath.getNameCount();
        if (nameCount == 1) {
            return relativePath.toString();
        }
        StringBuilder name = new StringBuilder();
        for (int i = 0; i < nameCount; i++) {
            name.append((i != 0) ? "." : "");
            String subPath = relativePath.getName(i).toString();
            if (i == nameCount - 1) {
                // remove file extension
                subPath = subPath.substring(0, subPath.lastIndexOf("."));
            }
            name.append(subPath);
        }
        return toKebabCase(name.toString());
    }

    static String toKebabCase(String input) {
        if (!StringUtils.hasText(input)) {
            return null;
        }
        int length = input.length();
        StringBuilder result = new StringBuilder(length * 2);
        int resultLength = 0;
        boolean wasPrevTranslated = false;
        for (int i = 0; i < length; i++) {
            char c = input.charAt(i);
            if (i > 0 && c == '_') {
                result.append('-');
                resultLength++;
                continue;
            }
            if (i > 0 || c != '-') {
                if (Character.isUpperCase(c)) {
                    if (!wasPrevTranslated && resultLength > 0 && result.charAt(resultLength - 1) != '-') {
                        result.append('-');
                        resultLength++;
                    }
                    c = Character.toLowerCase(c);
                    wasPrevTranslated = true;
                } else {
                    wasPrevTranslated = false;
                }
                result.append(c);
                resultLength++;
            }
        }
        return resultLength > 0 ? result.toString() : input;
    }

    private static boolean hasHiddenPathElement(Path path) {
        for (Path value : path) {
            if (value.toString().startsWith("..")) {
                return true;
            }
        }
        return false;
    }


    private static void appendToWatchableTargets(FileSystemWatchTarget.WatchTargetType type, String target, List<Path> filterFiles, Path rootDir) {
        FileSystemWatchTarget finalTarget = new FileSystemWatchTarget(type, target);
        finalTarget.setRootDir(rootDir);
        if (filterFiles != null) {
            finalTarget.setFilterFiles(filterFiles.stream()
                    .map(p -> trimRelativePathAndReplaceBackSlash(p.getFileName().toString()))
                    .collect(Collectors.toList()));
        }
        String dirKey = finalTarget.getNormalizedDir();
        if (WATCHABLE_TARGETS.containsKey(dirKey)) {
            FileSystemWatchTarget existing = WATCHABLE_TARGETS.get(dirKey);
            if (existing.getType() != finalTarget.getType()) {
                log.error("config.import/config.location has overlap, dynamic config validation failed: {}", dirKey);
            } else {
                existing.getFilterFiles().addAll(finalTarget.getFilterFiles());
            }
        } else {
            WATCHABLE_TARGETS.put(dirKey, finalTarget);
        }
    }

    private static void appendToWatchableTargets(FileSystemWatchTarget.WatchTargetType type, String target) {
        appendToWatchableTargets(type, target, null, null);
    }
}
