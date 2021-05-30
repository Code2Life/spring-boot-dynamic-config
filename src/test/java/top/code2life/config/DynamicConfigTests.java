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
import java.util.List;
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
    public void testConfigPropRemoveBoxedValue() throws Exception {
        assertEquals(3, testProperty.getBoxedIntVal());

        Map<String, Object> data = readYmlData("application.yml");
        Map<?, ?> myProp = (Map<?, ?>) data.get("myProp");
        myProp.remove("boxedIntVal");
        writeYmlData(data, "application.yml");
        Thread.sleep(1000);

        // because of Spring binder mechanism, value would not be removed as Property being removed
        assertEquals(3, testProperty.getBoxedIntVal());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testConfigPropRemoveNestedCollectionValue() throws Exception {
        assertEquals("a1", testProperty.getNested().getCollectionVal().get(0));
        Map<String, Object> data = readYmlData("application.yml");
        Map<?, ?> myProp = (Map<?, ?>) data.get("myProp");
        List<Object> collectionVal = (List<Object>) ((Map<?, ?>) myProp.get("nested")).get("collection-val");
        collectionVal.remove(0);
        writeYmlData(data, "application.yml");
        Thread.sleep(1000);
        assertEquals("a2", testProperty.getNested().getCollectionVal().get(0));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testConfigPropAddOrRemoveNestedCollectionValue() throws Exception {
        assertEquals("a2", testProperty.getNested().getCollectionVal().get(0));

        Map<String, Object> data = readYmlData("application.yml");
        Map<?, ?> myProp = (Map<?, ?>) data.get("myProp");
        List<Object> collectionVal = (List<Object>) ((Map<?, ?>) myProp.get("nested")).get("collection-val");
        collectionVal.remove(0);
        collectionVal.add("a3");
        writeYmlData(data, "application.yml");
        Thread.sleep(1000);

        assertEquals("a3", testProperty.getNested().getCollectionVal().get(0));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testConfigPropAddOrRemoveNestedMapValue() throws Exception {
        assertEquals("v1", testProperty.getNested().getMapVal().get("m1"));

        Map<String, Object> data = readYmlData("application.yml");
        Map<?, ?> myProp = (Map<?, ?>) data.get("myProp");
        Map<String, Object> mapVal = (Map<String, Object>) ((Map<?, ?>) myProp.get("nested")).get("mapVal");
        mapVal.remove("m1");
        writeYmlData(data, "application.yml");
        Thread.sleep(1000);

        assertNull(testProperty.getNested().getMapVal().get("m1"));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testConfigPropRemoveAddMapValue() throws Exception {
        assertEquals(2, testProperty.getIntVal());
        assertEquals("v3", testProperty.getMapVal().get("k3"));

        Map<String, Object> data = readYmlData("application.yml");
        Map<String, Object> myProp = (Map<String, Object>) data.get("myProp");
        myProp.remove("intVal");
        ((Map<String, Object>) myProp.get("map-val")).remove("k3");
        ((Map<String, Object>) myProp.get("map-val")).put("k4", "v4");
        writeYmlData(data, "application.yml");
        Thread.sleep(1000);

        assertEquals(2, testProperty.getIntVal());
        assertEquals("v4", testProperty.getMapVal().get("k4"));
        assertNull(testProperty.getMapVal().get("k3"));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testListValues() throws Exception {
        assertEquals("l1", testProperty.getListVal().get(0));

        Map<String, Object> data = readYmlData("application.yml");
        Map<?, ?> myProp = (Map<?, ?>) data.get("myProp");
        List<String> listVal = (List<String>) myProp.get("list-val");
        listVal.remove(0);
        listVal.add("l3");

        writeYmlData(data, "application.yml");
        Thread.sleep(1000);

        assertEquals("l2", testProperty.getListVal().get(0));
        assertEquals("l3", testProperty.getListVal().get(1));
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
