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

**If your Spring Boot version is 2.4 or lower, please use version 1.0.8**

Maven

```xml

<dependency>
    <groupId>top.code2life</groupId>
   <artifactId>spring-boot-dynamic-config</artifactId>
   <version>1.0.9</version>
</dependency>
```

Gradle

```groovy
implementation 'top.code2life:spring-boot-dynamic-config:1.0.9'
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

Since Version 1.0.9 and Spring Boot 2.4, 'spring.config.import' is available !

```bash
# config.import could be used with config.location TOGETHER
java -jar your-spring-boot-app.jar --spring.config.import=/path/to/configA.yaml,/path/to/configB.yaml
# or use the config tree feature
java -jar your-spring-boot-app.jar --spring.config.import=configtree:/path/to/conf-dir/
```

Then, modifications on /path/to/config/application-<some-profile>.yml will take effect and reflect on @DynamicConfig
beans **immediately**.

### Import Config Tree

When using '--spring.config.import=configtree:/path/to/conf-dir/', Spring Boot will load all files recursively.

It will result in ConfigTreePropertySource in Spring environments, with key of file path, for example:
If file is 'path/to/conf-dir/module-a/file-b.yaml', the property key is 'module-a.file-b.yaml', the value is the content
of the file.

Dynamic Config version 1.0.9 enhanced this feature, when properties or yaml files found in config tree, it will append
additional property sources, and will watch all file changes and then manipulate these property sources to keep them
Dynamic, so that you could use like following:

Suppose you have this file in spring.config.import directory '/path/to/conf-dir',

```yaml
# file: /path/to/conf-dir/module-a/file-b.yaml
prop-c-in-file: example-value

prop-obj-key-infile:
  fieldA: value
  fieldB: value
```

Just adding prefix it will work, the prefix is similar to the file's relative path module-a/file-b.yaml =>
module-a.file-b。

```java
@Value("${module-a.file-b.prop-c-in-file}")
@DynamicConfig
private String loadedByConfigTree

// or
@DynamicConfig
@ConfigurationProperties(prefix = "module-a.file-b.prop-obj-key-infile")
public class PropsLoadedByConfigTree {
   // ...
}
```

Refer: https://docs.spring.io/spring-boot/docs/2.7.3/reference/htmlsingle/#features.external-config.files.configtree

### Best Practices

- Configuration as Code, Everything as Code
- Configurations should be maintained in Git, rather than any GUI system.
- Configurations should be applied to dev/production environments by Continuous Delivery system.
- Git-Based DevOps workflow is the modern way of operating services, at scale.

## Implementation

1. Bean 'DynamicConfigPropertiesWatcher' will be initialized if 'spring.config.location/import' is specified
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

- √ SpringBoot 2.4.x, 2.5.x, 2.6.x, 2.7.x, 3.0.0 and Above (Use spring-boot-dynamic-config 1.0.9 and above)
- √ SpringBoot 2.3.x (spring-boot-dynamic-config version: <= 1.0.8)
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
