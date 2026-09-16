package ru.corelia.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import ru.corelia.configuration.ConfigurationLoader;
import java.nio.file.Path;

/** Each application context owns its immutable configuration; no process-global customer state. */
@Configuration
public class CustomerConfiguration {
    @Bean
    public ConfigurationLoader.LoadedConfiguration loadedCustomerConfiguration(CoreliaConfig environment) {
        String path = environment.value("CORELIA_CONFIG_PATH");
        if (path.isBlank()) throw new IllegalStateException("CORELIA_CONFIG_PATH must point to a customer configuration package");
        return new ConfigurationLoader().load(Path.of(path), "0.1.0");
    }
}
