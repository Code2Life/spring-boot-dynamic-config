package top.code2life.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.context.ApplicationEvent;

import java.util.Map;

/**
 * Application event that represents configuration file has been changed
 *
 * @author Code2Life
 * @see DynamicConfigPropertiesWatcher
 */
@Getter
@Setter
public class ConfigurationChangedEvent extends ApplicationEvent {

    /**
     * The diff properties, keys are normalized, values are newest values
     */
    private Map<Object, Object> diff;

    ConfigurationChangedEvent(Map<Object, Object> diff) {
        super(diff.keySet());
        this.diff = diff;
    }
}
