package top.code2life.config;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.springframework.core.env.PropertySource;

import java.nio.file.Path;

@Data
@AllArgsConstructor
class PropertySourceMeta {

    private PropertySource<?> propertySource;

    private Path filePath;

    private long lastModifyTime;

}