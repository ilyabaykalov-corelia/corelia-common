package ru.corelia.config;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/** Provider-neutral параметры runtime Corelia. */
@Component
public class CoreliaRuntimeConfig {
    private final Environment environment;

    public CoreliaRuntimeConfig(Environment environment) { this.environment = environment; }
    public String value(String name, String fallback) { return environment.getProperty(name, fallback); }
    public String value(String name) { return value(name, ""); }
    public long number(String name, long fallback) {
        String value = value(name);
        if (value.isBlank()) return fallback;
        try { return Long.parseLong(value); }
        catch (NumberFormatException error) { throw new IllegalArgumentException("Некорректный числовой параметр " + name); }
    }
    public String provider() {
        String selected = value("CORELIA_PROVIDER");
        return selected.isBlank() ? value("corelia.provider", "platform-v") : selected;
    }
    /**
     * Возвращает provider отдельной capability с временным fallback на прежний
     * единый выбор provider. Имя capability передаётся в kebab-case.
     */
    public String provider(String capability) {
        if (capability == null || capability.isBlank()) throw new IllegalArgumentException("Не указана capability provider");
        String normalized = capability.trim().toLowerCase(java.util.Locale.ROOT);
        String environmentName = "CORELIA_PROVIDER_" + normalized.replace('-', '_').toUpperCase(java.util.Locale.ROOT);
        String selected = value(environmentName);
        return selected.isBlank() ? value("corelia.provider." + normalized, provider()) : selected;
    }
}
