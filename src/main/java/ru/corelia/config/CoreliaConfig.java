package ru.corelia.config;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import ru.corelia.http.ApiException;

/** @deprecated Используйте {@link CoreliaRuntimeConfig}; сохранён для совместимости компонентов Corelia. */
@Deprecated
@Component
public class CoreliaConfig extends CoreliaRuntimeConfig {
    private final Environment environment;

    public CoreliaConfig(Environment environment) {
        super(environment); this.environment = environment;
    }

    public String value(String name, String fallback) {
        return environment.getProperty(name, fallback);
    }

    public String value(String name) {
        return value(name, "");
    }

    public long number(String name, long fallback) {
        String value = value(name);
        if (value.isBlank()) return fallback;
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException error) {
            throw new IllegalArgumentException("Некорректный числовой параметр " + name);
        }
    }

    public String required(String name, String value) {
        if (value == null || value.isBlank())
            throw new ApiException(
                    503, "Не настроен параметр " + name + " для Corelia");
        return value;
    }

    public String issuer() {
        return value("CORELIA_AUTH_ISSUER");
    }

    public String audience() {
        return value("CORELIA_AUTH_AUDIENCE", "corelia-web");
    }

    public java.util.Set<String> audiences() {
        String configured = value("CORELIA_AUTH_AUDIENCES", audience());
        return java.util.Arrays.stream(configured.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    public static String trim(String url) {
        return url.replaceAll("/+$", "");
    }
}
