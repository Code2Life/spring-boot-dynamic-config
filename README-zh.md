## Spring Boot Dynamic Config

一个注解实现SpringBoot应用的动态配置。

#### 对比 spring-cloud-starter-config

- 不需要SpringCloud ConfigServer配置中心服务
- 不需要SpringCloud依赖，不需要@RefreshScope注解

#### 对比阿里Nacos/携程Apollo
- 不需要配置中心服务器
- 不需要学习额外的注解和SDK API

#### 亮点

- 无侵入，兼容SpringBoot原生的配置获取方式
- 超轻量, 不依赖任何SpringBoot核心库以外的三方库
- 极易使用, 只提供一个简单的注解: @DynamicConfig；一个事件：ConfigurationChangedEvent

## 快速开始

### 步骤一：添加依赖spring-boot-dynamic-config

Maven

```xml
<dependency>
    <groupId>top.code2life</groupId>
    <artifactId>spring-boot-dynamic-config</artifactId>
    <version>1.0.1</version>
</dependency>
```

Gradle

```groovy
implementation 'top.code2life:spring-boot-dynamic-config:1.0.1'
```

### 步骤二：在代码中添加 @DynamicConfig 注解

使用方法1: 在包含@Value成员的类上添加 @DynamicConfig。

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

使用方法2: 在有 @ConfigurationProperties 注解的类上添加 @DynamicConfig。

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

### 步骤三：使用指定配置路径的方式启动SpringBoot应用

```bash
java -jar your-spring-boot-app.jar --spring.config.location=/path/to/config
```

启动后任何配置路径下的文件修改（/path/to/config/application-xxx.yml）都会在**注有@DynamicConfig的Spring Bean里立即生效**，getter方法可以直接获取到最新配置值。

### 配置管理的最佳实践
- 以代码的方式管理配置，Everything as Code；
- Git是维护配置信息的最佳版本控制系统，绝大多数应用，不需要配置管理中心这样的系统；
- 配置由**持续集成系统自动化部署**到开发/产线环境，而不是登陆到某个系统，手动输入配置值；
- Git Ops的方式做自动化运维，更符合DevOps的思想，在应用服务数量非常多的时候，也更具备伸缩性。

## 实现核心

1. 使用 --spring.config.location 参数启动时，初始化 DynamicConfigPropertiesWatcher 这个Bean；
2. 与此同时，初始化 DynamicConfigBeanPostProcessor 这个BeanPostProcessor，用来处理 @DynamicConfig；
3. DynamicConfigBeanPostProcessor 收集所有带有 @DynamicConfig 注解的Bean的元数据，包括Bean的名称、实例、@Value成员变量等等；
4. DynamicConfigPropertiesWatcher 开始监听 spring.config.location 目录下所有的文件变动，对于变化的Yaml/Properties，生成PropertySource动态替换Environment Bean中的PropertySource；
5. DynamicConfigPropertiesWatcher 在ApplicationContext中发布配置变动的Event：ConfigurationChangedEvent；
6. DynamicConfigBeanPostProcessor 订阅了上述事件，计算变动的文件，对哪些Key造成了差异
7. 对于每个有差异的Property Key, DynamicConfigBeanPostProcessor 比对记录的Bean元数据，找到相关联的Bean
8. 对于这次变动相关联的Bean, 调用反射方法，或 ConfigurationPropertiesBindingPostProcessor 的API，绑定到当前Bean的成员变量中，实现配置动态生效

## 兼容性

任何SpringBoot/SpringCloud应用都可以使用这个库，只要依赖的SpringBoot版本在SpringBoot 2.0以上即可。

- √ SpringBoot 2.5.x 及以上
- √ SpringBoot 2.4.x
- √ SpringBoot 2.3.x
- √ SpringBoot 2.2.x
- √ SpringBoot 2.1.x
- √ SpringBoot 2.0.x
- X SpringBoot 1.5.x 及以下，不支持

注意: 
- SpringBoot 2.0.x中不能使用JUnit5，只能使用JUnit4，因此在Spring 2.0.x版本跑本仓库里的单元测试，需要切换到JUnit4
- spring-boot-dynamic-config仅包含spring-boot库的编译依赖，不依赖任何其他三方库

## 开源许可证

Apache 2.0