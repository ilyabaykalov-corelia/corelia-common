package ru.corelia.config;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import ru.corelia.http.ApiException;

/** Настройки развёртывания адаптера Platform V. */
@Component
public class CoreliaConfig {
    private final Environment environment;

    public CoreliaConfig(Environment environment) {
        this.environment = environment;
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
                    503, "Не настроен параметр " + name + " для интеграции с Platform V");
        return value;
    }

    public String dataspace() {
        return value("PLATFORM_V_DATASPACE_GRAPHQL_URL");
    }

    public String bpmx() {
        return value("PLATFORM_V_BPMX_BASE_URL");
    }

    public String bpmu() {
        return value("PLATFORM_V_TASK_LIST_BASE_URL");
    }

    public String tenant() {
        return required("PLATFORM_V_TENANT", value("PLATFORM_V_TENANT"));
    }

    public String appId() {
        return required("PLATFORM_V_APP_INSTANCE_ID", value("PLATFORM_V_APP_INSTANCE_ID"));
    }

    public String keycloak(String kind) {
        String base = trim(value("PLATFORM_V_KEYCLOAK_BASE_URL"));
        String suffix =
                switch (kind) {
                    case "JWKS" -> "certs";
                    default -> kind.toLowerCase(java.util.Locale.ROOT);
                };
        return value(
                "PLATFORM_V_KEYCLOAK_" + kind + "_URL",
                base.isEmpty() ? "" : base + "/protocol/openid-connect/" + suffix);
    }

    public String issuer() {
        return value(
                "CORELIA_AUTH_ISSUER",
                value("PLATFORM_V_KEYCLOAK_ISSUER", trim(value("PLATFORM_V_KEYCLOAK_BASE_URL"))));
    }

    public String clientId() {
        return value("PLATFORM_V_KEYCLOAK_CLIENT_ID", "PlatformAuth-Proxy");
    }

    public static String trim(String url) {
        return url.replaceAll("/+$", "");
    }
}
