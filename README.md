## Spring Boot Dynamic Config

<p align="left">
<br>
<a href="https://github.com/code2life/spring-boot-dynamic-config"><img src="https://github.com/code2life/spring-boot-dynamic-config/actions/workflows/gradle.yml/badge.svg" /></a>
<a href="https://github.com/code2life/spring-boot-dynamic-config/actions/workflows/gradle.yml"><img src="https://filecdn.code2life.top/jacoco-sp.svg" /></a>
<a href="https://codebeat.co/projects/github-com-code2life-spring-boot-dynamic-config-main"><img alt="codebeat badge" src="https://codebeat.co/badges/ea7b2127-62f3-45f4-9f38-55f8203c0121" /></a>
<br>
</p>

Hot-reload your SpringBoot configurations, with just a '@DynamicConfig' annotation, the simplest solution, ever.

[English](https://github.com/Code2Life/spring-boot-dynamic-config/blob/main/README.md) [简体中文](https://github.com/Code2Life/spring-boot-dynamic-config/blob/main/README-zh.md)

- :heart: **Non-intrusive**, compatible with SpringBoot native ways (@Value, @ConfigurationProperties)
- :zap: **Lightweight & Blazing Fast**, depend on nothing but SpringBoot core libs
- :grinning: **Extremely easy to use**, only provide an annotation: @DynamicConfig, an event: ConfigurationChangedEvent
- ☸ Perfect solution for hot-reloading configuration of SpringBoot application on Kubernetes, with K8S ConfigMap

#### Compare with spring-cloud-starter-config

- No need for config server
- No SpringCloud dependency and @RefreshScope annotation, won't destroy and rebuild beans

#### Compare with Alibaba Nacos / Ctripcorp Apollo

- No need for Nacos/Apollo server
- No need for learning Annotations, Client APIs, etc.

## Demo

<img src="https://filecdn.code2life.top/springboot-config-demo.gif" alt="Demo" />

## Getting Started

### Step1. Add spring-boot-dynamic-config Dependency

Maven

```xml

<dependency>
    <groupId>top.code2life</groupId>
   <artifactId>spring-boot-dynamic-config</artifactId>
   <version>1.0.8</version>
</dependency>
```

Gradle

```groovy
implementation 'top.code2life:spring-boot-dynamic-config:1.0.8'
```

### Step2. Add @DynamicConfig Annotation

Option1: Add @DynamicConfig annotation on class which contains @Value field.

```java
import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import top.code2life.config.DynamicConfig;

import java.util.Set;

@Data
@Component
@DynamicConfig // add annotation here !
public class DynamicFeatures {

    @Value("${dynamic-test-plain:default}")
    private String plainValue;

    @Value("#{@featureGate.convert('${dynamic-feature-conf}')}")
    private Set<String> someBetaFeatureConfig;

    // @DynamicConfig // adding annotation here also works!
    @Value("#{@testComponent.transform(${dynamic.transform-a:20}, ${dynamic.transform-b:10})} ")
    private double transformBySpEL;


    public double transform(double t1, double t2) {
        return t1 / t2;
    }
}

// file: application-profile.yml
// ============================
// dynamic-test-plain: someVal # kebab-case is recommended
// dynamicFeatureConf: a,b,c  # camelCase compatible
// dynamic:
//   transform-a: 100
//   transform-b: 10
```

Option2: Add @ConfigurationProperties annotation on configuration class.

```java
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import top.code2life.config.DynamicConfig;

import java.util.Map;

@Data
@DynamicConfig  // add annotation here !
@Configuration
@ConfigurationProperties(prefix = "my-prop")
public class TestConfigurationProperties {

    private String str;

    private Double doubleVal;

    private Map<String, Object> mapVal;
}

// file: application-another-profile.yml
// ============================
// my-prop:  # or myProp, relax binding supported 
//   str: someVal
//   double-val: 100.0
//   mapVal:
//     k: v
```

### Step3. Run Application with Configuration Location

```bash
java -jar your-spring-boot-app.jar --spring.config.location=/path/to/config
```

Then, modifications on /path/to/config/application-<some-profile>.yml will take effect and reflect on @DynamicConfig
beans **immediately**.

### Best Practices

- Configuration as Code, Everything as Code
- Configurations should be maintained in Git, rather than any GUI system.
- Configurations should be applied to dev/production environments by Continuous Delivery system.
- Git-Based DevOps workflow is the modern way of operating services, at scale.

## Implementation

1. Bean 'DynamicConfigPropertiesWatcher' will be initialized if 'spring.config.location' is specified
2. Bean 'DynamicConfigBeanPostProcessor' will be initialized if 'DynamicConfigPropertiesWatcher' exists
3. DynamicConfigBeanPostProcessor collects beans' metadata after initializing
4. DynamicConfigPropertiesWatcher watches configuration directory, then replace PropertySource in Environment on changes
5. DynamicConfigPropertiesWatcher publishes 'ConfigurationChangedEvent'
6. DynamicConfigBeanPostProcessor listens 'ConfigurationChangedEvent', calculate diff
7. For each changed key, DynamicConfigBeanPostProcessor will use preserved bean metadata to check if it's related
8. After filtering related beans, it will use reflect API or ConfigurationPropertiesBindingPostProcessor API to modify
   fields of existing bean

## Compatibility

Any SpringBoot/SpringCloud application within following SpringBoot version can use this lib.

- √ SpringBoot 2.6.x, 2.7.x, 3.0.0 and Above
- √ SpringBoot 2.5.x 
- √ SpringBoot 2.4.x
- √ SpringBoot 2.3.x
- √ SpringBoot 2.2.x
- √ SpringBoot 2.1.x
- √ SpringBoot 2.0.x
- X SpringBoot 1.5.x and Lower

NOTES:

- For SpringBoot 2.0.x, use Junit4 rather than Junit5.
- This lib does not depend on ANY other libs except SpringBoot core libs.

## License

Spring Boot Dynamic Config is Open Source software released under
the [Apache 2.0 license](https://www.apache.org/licenses/LICENSE-2.0.html).
