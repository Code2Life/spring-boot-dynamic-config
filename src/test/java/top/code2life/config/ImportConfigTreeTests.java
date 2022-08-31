package top.code2life.config;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.context.TestPropertySource;
import top.code2life.config.sample.TestApplication;
import top.code2life.config.sample.configtree.TestConfTreeConfigurationProperties;
import top.code2life.config.sample.configtree.TestConfigTreeBeanConfiguration;
import top.code2life.config.sample.configtree.TestConfigTreeComponent;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static top.code2life.config.ImportConfigTreeTests.IMPORT_LOCATION;
import static top.code2life.config.TestUtils.*;

/**
 * Test new features in Spring Boot 2.4: spring.config.import feature
 * https://docs.spring.io/spring-boot/docs/2.7.3/reference/htmlsingle/#features.external-config.files.configtree
 */
@Slf4j
@TestPropertySource(
        properties = {
                "spring.config.location=" + DynamicConfigTests.NO_CONFIG_LOCATION,
                "spring.config.import=configtree:" + IMPORT_LOCATION
        }
)
@SpringBootTest(classes = {TestApplication.class})
public class ImportConfigTreeTests {

    public static final String IMPORT_LOCATION = "build/resources/test/conf-tree-test/";
    public static final String IMPORT_LOCATION_1 = "./build/resources/test/conf-tree-test/module_a/xyz.yaml";
    public static final String IMPORT_LOCATION_2 = "./build/resources/test/conf-tree-test/moduleB/abc.yaml";

    @Autowired
    private TestConfTreeConfigurationProperties testProperty;

    @Autowired
    private TestConfigTreeComponent testComponent;

    @Autowired
    private FeatureGate featureGate;

    @Autowired
    private TestConfigTreeBeanConfiguration testBeanConfig;

    @Autowired
    private TestConfigTreeBeanConfiguration.TestBean testBean;

    @Autowired
    private Environment env;

    @Test
    @SuppressWarnings("unchecked")
    public void testDynamicValueOnConfigurationProperties() throws Exception {
        String testVal = randomStr(8);
        Map<String, Object> data = readYmlData(IMPORT_LOCATION_2, "");
        Map<String, Object> myProp = (Map<String, Object>) data.get("myProp");
        myProp.put("str", testVal);
        writeYmlData(data, IMPORT_LOCATION_2, "");
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
        Map<String, Object> data = readYmlData(IMPORT_LOCATION_2, "");
        Map<String, Object> myProp = (Map<String, Object>) data.get("myProp");
        // should throw exception because of mal-type, won't impact running application
        myProp.put("double-val", testVal);
        writeYmlData(data, IMPORT_LOCATION_2, "");
        Thread.sleep(1000);
        assertEquals(1f, testProperty.getDoubleVal());
    }

    @Test
    public void testDynamicValuePlainText() throws Exception {
        assertEquals("dynamic-test", testComponent.getPlainValue());

        String testVal = randomStr(8);
        Map<String, Object> data = readYmlData(IMPORT_LOCATION_1, "");
        data.put("dynamicTestPlain", testVal);
        writeYmlData(data, IMPORT_LOCATION_1, "");
        Thread.sleep(1000);
        assertEquals(testVal, testComponent.getPlainValue());
    }

    @Test
    public void testDynamicValuePlainTextWithKebabCase() throws Exception {
        String testVal = randomStr(8);
        Map<String, Object> data = readYmlData(IMPORT_LOCATION_1, "");
        data.put("dynamic-test-plain", testVal);
        writeYmlData(data, IMPORT_LOCATION_1, "");
        Thread.sleep(1000);
        assertEquals(testVal, testComponent.getPlainValue());
    }

    @Test
    public void testConfigPropRemoveBoxedValue() throws Exception {
        assertEquals(3, testProperty.getBoxedIntVal());

        Map<String, Object> data = readYmlData(IMPORT_LOCATION_2, "");
        Map<?, ?> myProp = (Map<?, ?>) data.get("myProp");
        myProp.remove("boxedIntVal");
        writeYmlData(data, IMPORT_LOCATION_2, "");
        Thread.sleep(1000);

        // because of Spring binder mechanism, value would not be removed as Property being removed
        assertEquals(3, testProperty.getBoxedIntVal());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testConfigPropRemoveNestedCollectionValue() throws Exception {
        assertEquals("a1", testProperty.getNested().getCollectionVal().get(0));
        Map<String, Object> data = readYmlData(IMPORT_LOCATION_2, "");
        Map<?, ?> myProp = (Map<?, ?>) data.get("myProp");
        List<Object> collectionVal = (List<Object>) ((Map<?, ?>) myProp.get("nested")).get("collection-val");
        collectionVal.remove(0);
        writeYmlData(data, IMPORT_LOCATION_2, "");
        Thread.sleep(1000);
        assertEquals("a2", testProperty.getNested().getCollectionVal().get(0));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testConfigPropAddOrRemoveNestedCollectionValue() throws Exception {
        assertEquals("a2", testProperty.getNested().getCollectionVal().get(0));

        Map<String, Object> data = readYmlData(IMPORT_LOCATION_2, "");
        Map<?, ?> myProp = (Map<?, ?>) data.get("myProp");
        List<Object> collectionVal = (List<Object>) ((Map<?, ?>) myProp.get("nested")).get("collection-val");
        collectionVal.remove(0);
        collectionVal.add("a3");
        writeYmlData(data, IMPORT_LOCATION_2, "");
        Thread.sleep(1000);

        assertEquals("a3", testProperty.getNested().getCollectionVal().get(0));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testConfigPropAddOrRemoveNestedMapValue() throws Exception {
        assertEquals("v1", testProperty.getNested().getMapVal().get("m1"));

        Map<String, Object> data = readYmlData(IMPORT_LOCATION_2, "");
        Map<?, ?> myProp = (Map<?, ?>) data.get("myProp");
        Map<String, Object> mapVal = (Map<String, Object>) ((Map<?, ?>) myProp.get("nested")).get("mapVal");
        mapVal.remove("m1");
        writeYmlData(data, IMPORT_LOCATION_2, "");
        Thread.sleep(1000);

        assertNull(testProperty.getNested().getMapVal().get("m1"));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testConfigPropRemoveAddMapValue() throws Exception {
        assertEquals(2, testProperty.getIntVal());
        assertEquals("v3", testProperty.getMapVal().get("k3"));

        Map<String, Object> data = readYmlData(IMPORT_LOCATION_2, "");
        Map<String, Object> myProp = (Map<String, Object>) data.get("myProp");
        myProp.remove("intVal");
        ((Map<String, Object>) myProp.get("map-val")).remove("k3");
        ((Map<String, Object>) myProp.get("map-val")).put("k4", "v4");
        writeYmlData(data, IMPORT_LOCATION_2, "");
        Thread.sleep(1000);

        assertEquals(2, testProperty.getIntVal());
        assertEquals("v4", testProperty.getMapVal().get("k4"));
        assertNull(testProperty.getMapVal().get("k3"));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testListValues() throws Exception {
        assertEquals("l1", testProperty.getListVal().get(0));

        Map<String, Object> data = readYmlData(IMPORT_LOCATION_2, "");
        Map<?, ?> myProp = (Map<?, ?>) data.get("myProp");
        List<String> listVal = (List<String>) myProp.get("list-val");
        listVal.remove(0);
        listVal.add("l3");

        writeYmlData(data, IMPORT_LOCATION_2, "");
        Thread.sleep(1000);

        assertEquals("l2", testProperty.getListVal().get(0));
        assertEquals("l3", testProperty.getListVal().get(1));
    }

    @Test
    public void testDynamicValueOnEnvBean() throws Exception {
        String testVal = randomStr(8);
        Map<String, Object> data = readYmlData(IMPORT_LOCATION_2, "");
        data.put("dynamicEnvTest", testVal);
        writeYmlData(data, IMPORT_LOCATION_2, "");
        Thread.sleep(1000);
        assertEquals(testVal, env.getProperty("module-b.abc.dynamicEnvTest"));
    }

    @Test
    public void testDynamicValueOnFeatureGate() throws Exception {
        String testVal = randomStr(8);
        assertFalse(featureGate.isFeatureEnabled(testComponent.getSomeBetaFeatureConfig(), testVal));

        Map<String, Object> data = readYmlData(IMPORT_LOCATION_1, "");
        data.put("dynamicFeatureConf", data.get("dynamicFeatureConf") + ", " + testVal + " ,");
        writeYmlData(data, IMPORT_LOCATION_1, "");
        Thread.sleep(1000);

        assertTrue(featureGate.isFeatureEnabled(testComponent.getSomeBetaFeatureConfig(), testVal));
        assertFalse(featureGate.isFeatureEnabled(testVal));
        data.put(testVal, "True");
        writeYmlData(data, IMPORT_LOCATION_1, "");
        Thread.sleep(1000);
        assertTrue(featureGate.isFeatureEnabled("module-a.xyz." + testVal));

    }

    @Test
    @SuppressWarnings("unchecked")
    public void testMultipleDynamicValueOnSpEl() throws Exception {
        double testVal = randomDouble();
        double testVal2 = randomDouble();

        Map<String, Object> data = readYmlData(IMPORT_LOCATION_1, "");
        Map<String, Object> internal = (Map<String, Object>) data.get("dynamic");
        internal.put("transform-a", testVal);
        internal.put("transform-b", testVal2);
        writeYmlData(data, IMPORT_LOCATION_1, "");
        Thread.sleep(1000);

        assertEquals(testVal / testVal2, testComponent.getTransformBySpEL());

        double testVal3 = randomDouble();
        internal.put("transform-b", testVal3);
        writeYmlData(data, IMPORT_LOCATION_1, "");
        Thread.sleep(1000);

        assertEquals(testVal / testVal3, testComponent.getTransformBySpEL());
    }
}