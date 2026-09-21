package ru.corelia.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import ru.corelia.configuration.ConfigurationLoader;
import ru.corelia.configuration.DocumentTypeCatalog;
import java.nio.file.Path;

/** Каждый контекст приложения владеет неизменяемой конфигурацией без глобального состояния заказчика. */
@Configuration
public class CustomerConfiguration {
    @Bean
    public ConfigurationLoader.LoadedConfiguration loadedCustomerConfiguration(CoreliaConfig environment) {
        String path = environment.value("CORELIA_CONFIG_PATH");
        if (path.isBlank()) throw new IllegalStateException("CORELIA_CONFIG_PATH must point to a customer configuration package");
        return new ConfigurationLoader().load(Path.of(path), "0.1.0");
    }

    @Bean
    public DocumentTypeCatalog documentTypeCatalog(ConfigurationLoader.LoadedConfiguration configuration) {
        return new DocumentTypeCatalog(configuration);
    }
}
