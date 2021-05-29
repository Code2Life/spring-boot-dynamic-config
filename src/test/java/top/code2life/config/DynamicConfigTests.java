package top.code2life.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.test.context.TestPropertySource;
import org.yaml.snakeyaml.Yaml;
import top.code2life.config.sample.TestApplication;
import top.code2life.config.sample.TestBeanConfiguration;
import top.code2life.config.sample.TestComponent;
import top.code2life.config.sample.TestConfigurationProperties;

import java.io.*;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

@TestPropertySource(
        properties = {"spring.config.location=" + DynamicConfigTests.CONFIG_LOCATION}
)
@SpringBootTest(classes = {TestApplication.class})
public class DynamicConfigTests {

    public static final String CONFIG_LOCATION = "./build/resources/test/";

    private static final Yaml YAML = new Yaml();
    private static final Random RANDOM = new Random();

    @Autowired
    private ApplicationContext context;

    @Autowired
    private TestConfigurationProperties testProperty;

    @Autowired
    private TestComponent testComponent;

    @Autowired
    private FeatureGate featureGate;

    @Autowired
    private TestBeanConfiguration testBeanConfig;

    @Autowired
    private TestBeanConfiguration.TestBean testBean;

    @Autowired
    private Environment env;

    @Test
    public void testBeanLoaded() {
        DynamicConfigPropertiesWatcher bean = context.getBean(DynamicConfigPropertiesWatcher.class);
        FeatureGate featureGate = context.getBean(FeatureGate.class);
        DynamicConfigBeanPostProcessor processor = context.getBean(DynamicConfigBeanPostProcessor.class);
        assertNotNull(bean);
        assertNotNull(featureGate);
        assertNotNull(processor);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testDynamicValueOnConfigurationProperties() throws Exception {
        assertEquals("dynamic", testBean.getInternalStr());
        assertEquals("dynamic", testBeanConfig.getCurrentStrValue());
        assertEquals("dynamic", testProperty.getStr());

        String testVal = randomStr(8);
        Map<String, Object> data = readYmlData("application.yml");
        Map<String, Object> myProp = (Map<String, Object>) data.get("myProp");
        myProp.put("str", testVal);
        writeYmlData(data, "application.yml");
        Thread.sleep(1000);
        assertEquals(testVal, testProperty.getStr());
        // initialized bean won't be refreshed with new value, should take effect on injected bean
        assertEquals("dynamic", testBean.getInternalStr());
        assertEquals(testVal, testBeanConfig.getCurrentStrValue());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testDynamicValueWithWrongType() throws Exception {
        String testVal = randomStr(8);
        Map<String, Object> data = readYmlData("application.yml");
        Map<String, Object> myProp = (Map<String, Object>) data.get("myProp");
        // should throw exception because of mal-type, won't impact running application
        myProp.put("double-val", testVal);
        writeYmlData(data, "application.yml");
        Thread.sleep(1000);
        assertEquals(1f, testProperty.getDoubleVal());
    }

    @Test
    public void testDynamicValuePlainText() throws Exception {
        assertEquals("dynamic-test", testComponent.getPlainValue());

        String testVal = randomStr(8);
        Map<String, Object> data = readYmlData("application-dynamic.yml");
        data.put("dynamicTestPlain", testVal);
        writeYmlData(data, "application-dynamic.yml");
        Thread.sleep(1000);
        assertEquals(testVal, testComponent.getPlainValue());
    }

    @Test
    public void testDynamicValuePlainTextWithKebabCase() throws Exception {
        String testVal = randomStr(8);
        Map<String, Object> data = readYmlData("application-dynamic.yml");
        data.put("dynamic-test-plain", testVal);
        writeYmlData(data, "application-dynamic.yml");
        Thread.sleep(1000);
        assertEquals(testVal, testComponent.getPlainValue());
    }

    @Test
    public void testDynamicValueOnEnvBean() throws Exception {
        String testVal = randomStr(8);
        Map<String, Object> data = readYmlData("application.yml");
        data.put("dynamicEnvTest", testVal);
        writeYmlData(data, "application.yml");
        Thread.sleep(1000);
        assertEquals(testVal, env.getProperty("dynamicEnvTest"));
    }

    @Test
    public void testDynamicValueOnFeatureGate() throws Exception {
        String testVal = randomStr(8);
        assertFalse(featureGate.isFeatureEnabled(testComponent.getSomeBetaFeatureConfig(), testVal));

        Map<String, Object> data = readYmlData("application-dynamic.yml");
        data.put("dynamicFeatureConf", data.get("dynamicFeatureConf") + ", " + testVal + " ,");
        writeYmlData(data, "application-dynamic.yml");
        Thread.sleep(1000);

        assertTrue(featureGate.isFeatureEnabled(testComponent.getSomeBetaFeatureConfig(), testVal));
        assertFalse(featureGate.isFeatureEnabled(testVal));
        data.put(testVal, "True");
        writeYmlData(data, "application-dynamic.yml");
        Thread.sleep(1000);
        assertTrue(featureGate.isFeatureEnabled(testVal));

    }

    @Test
    @SuppressWarnings("unchecked")
    public void testMultipleDynamicValueOnSpEl() throws Exception {
        double testVal = randomDouble();
        double testVal2 = randomDouble();

        Map<String, Object> data = readYmlData("application-dynamic.yml");
        Map<String, Object> internal = (Map<String, Object>) data.get("dynamic");
        internal.put("transform-a", testVal);
        internal.put("transform-b", testVal2);
        writeYmlData(data, "application-dynamic.yml");
        Thread.sleep(1000);

        assertEquals(testVal / testVal2, testComponent.getTransformBySpEL());

        double testVal3 = randomDouble();
        internal.put("transform-b", testVal3);
        writeYmlData(data, "application-dynamic.yml");
        Thread.sleep(1000);

        assertEquals(testVal / testVal3, testComponent.getTransformBySpEL());
    }

    private Map<String, Object> readYmlData(String APPLICATION_YML) throws IOException {
        File file = new File(CONFIG_LOCATION, APPLICATION_YML);
        try (InputStream inputStream = new FileInputStream(file)) {
            return YAML.load(inputStream);
        }
    }

    private void writeYmlData(Map<String, Object> data, String APPLICATION_YML) throws IOException {
        File file = new File(CONFIG_LOCATION, APPLICATION_YML);
        try (PrintWriter writer = new PrintWriter(file)) {
            YAML.dump(data, writer);
        }
    }

    private String randomStr(int len) {
        int leftLimit = 97; // letter 'a'
        int rightLimit = 122; // letter 'z'
        return RANDOM.ints(leftLimit, rightLimit + 1)
                .limit(len)
                .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
                .toString();
    }

    private Double randomDouble() {
        return RANDOM.nextDouble();
    }
}
