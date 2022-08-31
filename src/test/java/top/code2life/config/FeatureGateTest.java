package top.code2life.config;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Code2Life
 **/
public class FeatureGateTest {

    private final FeatureGate featureGate = new FeatureGate(null);

    @Test
    public void testConvertEmptyConfigValue() {
        Set<String> configVal = featureGate.convert("");
        assertEquals(0, configVal.size());
    }

    @Test
    public void testGetBetaListOfFeature() {
        Set<String> configVal = featureGate.convert("a, b ,c");
        assertTrue(featureGate.isFeatureEnabled(configVal, "a"));
        assertFalse(featureGate.isFeatureEnabled(configVal, "d"));
    }

    @Test
    public void testFullyOpenFeature() {
        Set<String> configVal = featureGate.convert("a, b ,all");
        assertTrue(featureGate.isFeatureEnabled(configVal, "a"));
        assertTrue(featureGate.isFeatureEnabled(configVal, "e"));
    }

    @Test
    public void testToKebabCase() {
        assertEquals("abc-ef-g-x-a-bbcc-ef-z.ac.%", ConfigurationUtils.normalizePropKey("Abc_EfG-xA-bbcc_EF-z.ac.%"));
    }
}
