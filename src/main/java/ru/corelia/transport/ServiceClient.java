package ru.corelia.transport;

import static ru.corelia.support.Json.*;

import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import ru.corelia.auth.AuthContext;
import ru.corelia.config.CoreliaConfig;
import ru.corelia.http.ApiException;
import ru.corelia.support.LogJson;

import tools.jackson.databind.JsonNode;

import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.security.KeyStore;
import java.time.Duration;
import java.util.*;

import javax.net.ssl.*;

/**
 * Внутренний HTTP-клиент: взаимная TLS-аутентификация, фиксированные адреса и отсутствие повторов
 * команд.
 */
@Component
public class ServiceClient implements AutoCloseable {
    @jakarta.annotation.PreDestroy
    public synchronized void close() {
        if (client != null) {
            client.close();
            client = null;
        }
    }

    private final CoreliaConfig config;
    private volatile HttpClient client;

    public ServiceClient(CoreliaConfig config) {
        this.config = config;
    }

    private synchronized HttpClient client() {
        if (client != null) return client;
        try {
            char[] password = config.value("corelia.tls.password").toCharArray();
            KeyStore keys = KeyStore.getInstance("PKCS12"), trust = KeyStore.getInstance("PKCS12");
            try (var in =
                    Files.newInputStream(Path.of(config.value("corelia.internal.key-store")))) {
                keys.load(in, password);
            }
            try (var in =
                    Files.newInputStream(Path.of(config.value("corelia.internal.trust-store")))) {
                trust.load(in, password);
            }
            var km = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            km.init(keys, password);
            var tm = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tm.init(trust);
            var ssl = SSLContext.getInstance("TLS");
            ssl.init(km.getKeyManagers(), tm.getTrustManagers(), null);
            client =
                    HttpClient.newBuilder()
                            .sslContext(ssl)
                            .followRedirects(HttpClient.Redirect.NEVER)
                            .connectTimeout(Duration.ofSeconds(5))
                            .build();
            return client;
        } catch (Exception error) {
            throw new IllegalStateException("Не удалось настроить mTLS-клиент Corelia", error);
        }
    }

    public HttpResponse<byte[]> raw(
            String target, String path, String method, byte[] bytes, AuthContext auth) {
        String base =
                config.required(
                        "corelia.services." + target, config.value("corelia.services." + target));
        URI uri = URI.create(base + path);
        if (!uri.getScheme().equals("https")
                || uri.getUserInfo() != null
                || !path.startsWith("/internal/v1/"))
            throw new IllegalStateException("Внутренний адрес должен использовать HTTPS");
        long startedAt = System.nanoTime();
        LogJson.info(
                "Calling Corelia service",
                object(
                        "service", target,
                        "url", LogJson.upstreamTarget(uri.toString()),
                        "method", method));
        try {
            var request =
                    HttpRequest.newBuilder(uri)
                            .timeout(
                                    Duration.ofMillis(
                                            config.number("corelia.internal.timeout-ms", 60000)))
                            .header("Content-Type", "application/json")
                            .header("Accept", "application/json")
                            .method(
                                    method,
                                    bytes.length == 0
                                            ? HttpRequest.BodyPublishers.noBody()
                                            : HttpRequest.BodyPublishers.ofByteArray(bytes));
            if (auth != null) request.header("Authorization", auth.authorization());
            String requestId = MDC.get("requestId");
            if (requestId != null && !requestId.isBlank())
                request.header("X-Request-Id", requestId);
            var response = client().send(request.build(), HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                String message = "Ошибка внутреннего сервиса " + target;
                try {
                    message = fallback(text(MAPPER.readTree(response.body()), "message"), message);
                } catch (RuntimeException ignored) {
                }
                throw new ApiException(
                        response.statusCode() >= 300 && response.statusCode() < 400
                                ? 502
                                : response.statusCode(),
                        message);
            }
            LogJson.info(
                    "Corelia service completed",
                    object(
                            "service", target,
                            "url", LogJson.upstreamTarget(uri.toString()),
                            "method", method,
                            "status", response.statusCode(),
                            "durationMs", (System.nanoTime() - startedAt) / 1_000_000));
            return response;
        } catch (ApiException error) {
            LogJson.info(
                    "Corelia service failed",
                    object(
                            "service", target,
                            "url", LogJson.upstreamTarget(uri.toString()),
                            "method", method,
                            "status", error.status(),
                            "durationMs", (System.nanoTime() - startedAt) / 1_000_000,
                            "message", error.getMessage()));
            throw error;
        } catch (HttpTimeoutException error) {
            LogJson.info(
                    "Corelia service failed",
                    object(
                            "service", target,
                            "url", LogJson.upstreamTarget(uri.toString()),
                            "method", method,
                            "status", 504,
                            "durationMs", (System.nanoTime() - startedAt) / 1_000_000,
                            "message", "Истекло время ожидания сервиса " + target));
            throw new ApiException(504, "Истекло время ожидания сервиса " + target);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            LogJson.info(
                    "Corelia service failed",
                    object(
                            "service", target,
                            "url", LogJson.upstreamTarget(uri.toString()),
                            "method", method,
                            "status", 503,
                            "durationMs", (System.nanoTime() - startedAt) / 1_000_000,
                            "message", "Вызов сервиса прерван"));
            throw new ApiException(503, "Вызов сервиса прерван");
        } catch (IOException error) {
            LogJson.info(
                    "Corelia service failed",
                    object(
                            "service", target,
                            "url", LogJson.upstreamTarget(uri.toString()),
                            "method", method,
                            "status", 502,
                            "durationMs", (System.nanoTime() - startedAt) / 1_000_000,
                            "message", "Сервис " + target + " недоступен"));
            throw new ApiException(502, "Сервис " + target + " недоступен");
        }
    }

    public JsonNode call(
            String target, String path, String method, JsonNode body, AuthContext auth) {
        byte[] bytes =
                body == null
                        ? new byte[0]
                        : write(body).getBytes(java.nio.charset.StandardCharsets.UTF_8);
        var response = raw(target, path, method, bytes, auth);
        if (response.body().length == 0) return object();
        try {
            return MAPPER.readTree(response.body());
        } catch (RuntimeException error) {
            throw new ApiException(502, "Сервис " + target + " вернул некорректный JSON");
        }
    }
}
