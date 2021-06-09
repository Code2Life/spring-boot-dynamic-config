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
public class ConfigurationFileChangedEvent extends ApplicationEvent {

    /**
     * previous property source of changed config file
     */
    private PropertySource<?> previous;

    /**
     * current property source of changed config file
     */
    private PropertySource<?> current;

    ConfigurationFileChangedEvent(String path, PropertySource<?> previous, PropertySource<?> current) {
        super(path);
        this.previous = previous;
        this.current = current;
    }
}
