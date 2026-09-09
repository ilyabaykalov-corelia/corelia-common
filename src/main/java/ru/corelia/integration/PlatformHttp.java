package ru.corelia.integration;

import static ru.corelia.support.Json.*;

import org.springframework.stereotype.Component;

import ru.corelia.auth.AuthContext;
import ru.corelia.http.ApiException;

import tools.jackson.databind.JsonNode;

import java.io.IOException;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;

/** Единый транспорт: пользовательский токен, ограничение времени, запрет редиректов. */
@Component
public class PlatformHttp {
    private final HttpClient client =
            HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(30))
                    .followRedirects(HttpClient.Redirect.NEVER)
                    .build();

    public JsonNode platform(String url, String method, JsonNode body, AuthContext auth) {
        return platform(url, method, body, auth, Map.of());
    }

    public JsonNode platform(
            String url, String method, JsonNode body, AuthContext auth, Map<String, String> extra) {
        Map<String, String> headers = new LinkedHashMap<>(extra);
        headers.put("Authorization", auth.authorization());
        headers.putIfAbsent("Accept", "application/json");
        headers.put("Content-Type", "application/json");
        return json(url, method, body == null ? null : write(body), headers);
    }

    public JsonNode json(String url, String method, String body, Map<String, String> headers) {
        byte[] bytes =
                body == null ? new byte[0] : body.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        var response = raw(url, method, bytes, headers);
        if (response.body().length == 0) return object();
        try {
            return MAPPER.readTree(response.body());
        } catch (RuntimeException error) {
            throw new ApiException(
                    502, "Platform V вернул не JSON-ответ. Проверьте URL и авторизацию API");
        }
    }

    public HttpResponse<byte[]> raw(
            String url, String method, byte[] body, Map<String, String> headers) {
        try {
            var builder =
                    HttpRequest.newBuilder(URI.create(url))
                            .timeout(Duration.ofSeconds(30))
                            .method(
                                    method,
                                    body.length == 0
                                            ? HttpRequest.BodyPublishers.noBody()
                                            : HttpRequest.BodyPublishers.ofByteArray(body));
            headers.forEach(builder::header);
            var response = client.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray());
            int status = response.statusCode();
            if (status >= 300 && status < 400) {
                String location = response.headers().firstValue("location").orElse("");
                boolean authRedirect =
                        location.matches("(?is).*(openid-connect/auth|PlatformAuth|login).*");
                throw new ApiException(
                        502,
                        authRedirect
                                ? "Platform V API не принял Keycloak access token и вернул редирект"
                                        + " на авторизацию. Проверьте API route"
                                : "Platform V API вернул редирект");
            }
            if (status < 200 || status >= 300) {
                String raw = new String(response.body(), java.nio.charset.StandardCharsets.UTF_8);
                String message;
                try {
                    message = errorMessage(parse(raw));
                } catch (RuntimeException error) {
                    message = normalizeText(raw);
                }
                throw new ApiException(
                        status >= 500 ? 502 : status,
                        fallback(message, "Platform V API вернул HTTP " + status));
            }
            return response;
        } catch (HttpTimeoutException error) {
            throw new ApiException(504, "Истекло время ожидания ответа Platform V");
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new ApiException(503, "Вызов Platform V прерван при остановке Corelia");
        } catch (IOException | IllegalArgumentException error) {
            throw new ApiException(
                    502, "Ошибка вызова Platform V: " + error.getClass().getSimpleName());
        }
    }

    public static String errorMessage(JsonNode payload) {
        if (payload == null) return "";
        if (payload.isTextual()) return normalizeText(text(payload));
        String message = first(payload, "message", "error_description", "error");
        if (!message.isEmpty()) return message;
        return String.join(
                "; ",
                list(payload.path("errors")).stream()
                        .map(item -> text(item, "message"))
                        .filter(s -> !s.isEmpty())
                        .toList());
    }

    private static String normalizeText(String value) {
        String clean =
                value.replaceAll("(?is)<script.*?</script>|<style.*?</style>|<[^>]+>", " ")
                        .replaceAll("\\s+", " ")
                        .trim();
        return clean.length() > 700 ? clean.substring(0, 700) + "..." : clean;
    }
}
