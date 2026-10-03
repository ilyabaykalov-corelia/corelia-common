package ru.corelia.auth;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import ru.corelia.config.CoreliaAuthConfig;

/** Подключает provider-neutral настройки проверки JWT для сервисов Corelia. */
@Configuration
public class CoreliaAuthProviderConfiguration {
    @Bean
    AuthIdentityProvider coreliaAuthIdentityProvider(CoreliaAuthConfig auth) {
        return new AuthIdentityProvider() {
            @Override public String issuer() { return auth.issuer(); }
            @Override public java.util.Set<String> audiences() { return auth.audiences(); }
        };
    }

    @Bean
    AuthKeyProvider coreliaAuthKeyProvider(CoreliaAuthConfig auth) {
        return auth::jwksUrl;
    }
}
