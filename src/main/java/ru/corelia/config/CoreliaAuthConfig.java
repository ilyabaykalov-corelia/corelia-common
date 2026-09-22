package ru.corelia.config;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/** Provider-neutral параметры проверки access token. */
@Component
public class CoreliaAuthConfig {
    private final Environment environment;
    public CoreliaAuthConfig(Environment environment) { this.environment = environment; }
    public String jwksUrl() { return environment.getProperty("CORELIA_AUTH_JWKS_URL", ""); }
}
