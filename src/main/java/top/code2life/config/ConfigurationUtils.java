package top.code2life.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.support.AopUtils;
import org.springframework.util.ClassUtils;
import org.springframework.util.StringUtils;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Code2Life
 **/
@Slf4j
public class ConfigurationUtils {

    static final String VALUE_EXPR_PREFIX = "$";
    static final String SP_EL_PREFIX = "#";

    private static final Pattern VALUE_PATTERN = Pattern.compile("\\$\\{([^:}]+):?([^}]*)}");
    private static final Pattern CAMEL_CASE_PATTERN = Pattern.compile("([^A-Z-])([A-Z])");

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
        if (!StringUtils.hasText(name)) {
            return name;
        }
        Matcher matcher = CAMEL_CASE_PATTERN.matcher(name);
        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            matcher.appendReplacement(result, matcher.group(1) + '-' + StringUtils.uncapitalize(matcher.group(2)));
        }
        matcher.appendTail(result);
        return result.toString().replaceAll("_", "-").toLowerCase(Locale.ENGLISH);
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
        str = str.toLowerCase();
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
}
