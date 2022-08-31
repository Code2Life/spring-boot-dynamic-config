package top.code2life.config;

import org.yaml.snakeyaml.Yaml;

import java.io.*;
import java.util.Map;
import java.util.Random;

/**
 * @author Code2Life
 **/
public class TestUtils {

    private static final Yaml YAML = new Yaml();
    private static final Random RANDOM = new Random();

    public static Map<String, Object> readYmlData(String basePath, String confFilePath) throws IOException {
        File file = new File(basePath, confFilePath);
        try (InputStream inputStream = new FileInputStream(file)) {
            return YAML.load(inputStream);
        }
    }

    public static void writeYmlData(Map<String, Object> data, String basePath, String confFilePath) throws IOException {
        File file = new File(basePath, confFilePath);
        try (PrintWriter writer = new PrintWriter(file)) {
            YAML.dump(data, writer);
        }
    }

    public static String randomStr(int len) {
        int leftLimit = 97; // letter 'a'
        int rightLimit = 122; // letter 'z'
        return RANDOM.ints(leftLimit, rightLimit + 1)
                .limit(len)
                .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
                .toString();
    }

    public static Double randomDouble() {
        return RANDOM.nextDouble();
    }
}
