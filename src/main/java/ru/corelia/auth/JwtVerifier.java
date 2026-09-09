package ru.corelia.auth;

import static ru.corelia.support.Json.*;

import org.springframework.stereotype.Service;

import ru.corelia.config.CoreliaConfig;
import ru.corelia.http.ApiException;
import ru.corelia.integration.PlatformHttp;

import tools.jackson.databind.JsonNode;

import java.math.BigInteger;
import java.security.*;
import java.security.spec.RSAPublicKeySpec;
import java.time.Instant;
import java.util.*;

/** Локальная проверка RS256/JWKS без сетевого вызова сервиса авторизации на каждый запрос. */
@Service
public class JwtVerifier {
    private static final long CLOCK_SKEW_SECONDS = 30;
    private final CoreliaConfig config;
    private final PlatformHttp http;
    private List<JsonNode> keys = List.of();
    private long keysExpireAt;

    public JwtVerifier(CoreliaConfig config, PlatformHttp http) {
        this.config = config;
        this.http = http;
    }

    public AuthContext authenticate(String authorization) {
        if (authorization == null || !authorization.matches("(?i)^Bearer\\s+.+$")) {
            throw new ApiException(401, "Не передан Keycloak access token");
        }
        String token = authorization.replaceFirst("(?i)^Bearer\\s+", "");
        String[] parts = token.split("\\.", -1);
        if (parts.length != 3
                || Arrays.stream(parts).anyMatch(part -> !part.matches("[A-Za-z0-9_-]+")))
            invalidToken();
        JsonNode header = decode(parts[0]);
        JsonNode payload = decode(parts[1]);
        if (!"RS256".equals(text(header, "alg")) || !header.path("kid").isTextual()) invalidToken();
        String issuer = config.required("PLATFORM_V_KEYCLOAK_ISSUER", config.issuer());
        if (!issuer.equals(payload.path("iss").asString("")))
            throw new ApiException(401, "Keycloak access token выпущен неизвестным issuer");
        long now = Instant.now().getEpochSecond();
        if (!payload.path("exp").isNumber()
                || payload.path("exp").asDouble() <= now - CLOCK_SKEW_SECONDS) {
            throw new ApiException(401, "Срок действия Keycloak access token истек или не указан");
        }
        if (payload.has("nbf")
                && (!payload.path("nbf").isNumber()
                        || payload.path("nbf").asDouble() > now + CLOCK_SKEW_SECONDS)) {
            throw new ApiException(401, "Keycloak access token еще не действует");
        }
        JsonNode key = key(text(header, "kid"));
        try {
            var decoder = Base64.getUrlDecoder();
            var spec =
                    new RSAPublicKeySpec(
                            new BigInteger(1, decoder.decode(text(key, "n"))),
                            new BigInteger(1, decoder.decode(text(key, "e"))));
            var verifier = Signature.getInstance("SHA256withRSA");
            verifier.initVerify(KeyFactory.getInstance("RSA").generatePublic(spec));
            verifier.update(
                    (parts[0] + "." + parts[1])
                            .getBytes(java.nio.charset.StandardCharsets.US_ASCII));
            if (!verifier.verify(decoder.decode(parts[2])))
                throw new ApiException(401, "Подпись Keycloak access token не прошла проверку");
        } catch (GeneralSecurityException | IllegalArgumentException error) {
            throw new ApiException(401, "Некорректный ключ или подпись Keycloak access token");
        }
        return context(token, payload);
    }

    private synchronized JsonNode key(String kid) {
        if (keysExpireAt <= System.currentTimeMillis()) loadKeys();
        Optional<JsonNode> found =
                keys.stream().filter(item -> kid.equals(text(item, "kid"))).findFirst();
        if (found.isEmpty()) {
            loadKeys();
            found = keys.stream().filter(item -> kid.equals(text(item, "kid"))).findFirst();
        }
        JsonNode key =
                found.orElseThrow(
                        () ->
                                new ApiException(
                                        401, "Keycloak access token подписан неизвестным ключом"));
        if (!"RSA".equals(text(key, "kty"))
                || text(key, "n").isEmpty()
                || text(key, "e").isEmpty()) {
            throw new ApiException(401, "Некорректный ключ подписи Keycloak access token");
        }
        return key;
    }

    private void loadKeys() {
        var payload =
                http.json(
                        config.required(
                                "PLATFORM_V_KEYCLOAK_JWKS_URL",
                                config.value("CORELIA_AUTH_JWKS_URL", config.keycloak("JWKS"))),
                        "GET",
                        null,
                        Map.of("Accept", "application/json"));
        keys = list(payload.path("keys"));
        if (keys.isEmpty())
            throw new ApiException(502, "Keycloak JWKS не содержит ключей проверки access token");
        keysExpireAt = System.currentTimeMillis() + 300_000;
    }

    private AuthContext context(String token, JsonNode payload) {
        String login =
                fallback(first(payload, "preferred_username", "login", "email"), "anonymous");
        Set<String> roles = new LinkedHashSet<>();
        stringValues(payload.path("realm_access").path("roles"))
                .forEach(role -> addRole(roles, role));
        payload.path("resource_access")
                .properties()
                .forEach(
                        entry ->
                                stringValues(entry.getValue().path("roles"))
                                        .forEach(role -> addRole(roles, role)));
        return new AuthContext(
                token,
                fallback(text(payload, "sub"), login),
                login,
                fallback(first(payload, "name", "given_name"), login),
                text(payload, "email"),
                List.copyOf(roles),
                first(payload, "preferred_username", "login", "email", "sub"));
    }

    private static List<JsonNode> stringValues(JsonNode value) {
        return value.isArray() ? list(value) : value.isTextual() ? List.of(value) : List.of();
    }

    private static void addRole(Set<String> roles, JsonNode role) {
        if (!text(role).isEmpty()) roles.add(text(role));
    }

    private static JsonNode decode(String encoded) {
        try {
            JsonNode node = MAPPER.readTree(Base64.getUrlDecoder().decode(encoded));
            if (node == null || !node.isObject()) invalidToken();
            return node;
        } catch (RuntimeException error) {
            throw new ApiException(401, "Некорректный Keycloak access token");
        }
    }

    private static void invalidToken() {
        throw new ApiException(401, "Некорректный Keycloak access token");
    }
}
