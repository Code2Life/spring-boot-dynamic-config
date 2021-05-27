package top.code2life.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.context.ApplicationEvent;
import org.springframework.core.env.PropertySource;

/**
 * Application event that represents configuration file has been changed
 *
 * @author Code2Life
 * @see DynamicConfigPropertiesWatcher
 */
@Getter
@Setter
public class ConfigurationChangedEvent extends ApplicationEvent {
    private PropertySource<?> previous;
    private PropertySource<?> current;

    public ConfigurationChangedEvent(String path) {
        super(path);
    }
}
