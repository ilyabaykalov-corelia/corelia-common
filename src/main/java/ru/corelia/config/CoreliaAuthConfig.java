package ru.corelia.config;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/** Provider-neutral параметры проверки access token. */
@Component
public class CoreliaAuthConfig {
    private final Environment environment;
    public CoreliaAuthConfig(Environment environment) { this.environment = environment; }
    public String jwksUrl() { return environment.getProperty("CORELIA_AUTH_JWKS_URL", ""); }
    public String issuer() { return environment.getProperty("CORELIA_AUTH_ISSUER", ""); }
    public java.util.Set<String> audiences() {
        String configured = environment.getProperty("CORELIA_AUTH_AUDIENCES", environment.getProperty("CORELIA_AUTH_AUDIENCE", "corelia-web"));
        return java.util.Arrays.stream(configured.split(",")).map(String::trim).filter(value -> !value.isEmpty()).collect(java.util.stream.Collectors.toUnmodifiableSet());
    }
}
