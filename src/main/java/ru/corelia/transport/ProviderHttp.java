package ru.corelia.transport;

import static ru.corelia.support.Json.object;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.Map;
import org.springframework.stereotype.Component;
import ru.corelia.http.ApiException;
import ru.corelia.observability.CoreliaObservability;
import ru.corelia.observability.TraceContextPropagation;
import tools.jackson.databind.JsonNode;

/** Нейтральный HTTP-транспорт для общих интеграций Corelia. */
@Component
public class ProviderHttp {
    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();
    private final CoreliaObservability observability;
    private final TraceContextPropagation traceContext;

    public ProviderHttp(CoreliaObservability observability, TraceContextPropagation traceContext) {
        this.observability = observability;
        this.traceContext = traceContext;
    }

    public JsonNode json(String url, String method, String body, Map<String, String> headers) {
        try {
            var builder = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(30))
                    .method(method, body == null
                            ? HttpRequest.BodyPublishers.noBody()
                            : HttpRequest.BodyPublishers.ofString(body));
            headers.forEach(builder::header);
            traceContext.inject(builder);
            var response = observability.observe("provider.http", () -> {
                try {
                    return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
                } catch (IOException error) {
                    throw new ProviderCallException(error);
                } catch (InterruptedException error) {
                    Thread.currentThread().interrupt();
                    throw new ProviderCallException(error);
                }
            });
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new ApiException(response.statusCode() >= 500 ? 502 : response.statusCode(),
                        "Внешний provider вернул HTTP " + response.statusCode());
            }
            observability.externalRequest("provider", "http", "success");
            return response.body().isBlank() ? object() : ru.corelia.support.Json.MAPPER.readTree(response.body());
        } catch (ApiException error) {
            observability.externalRequest("provider", "http", error.status() == 504 ? "timeout" : "error");
            throw error;
        } catch (ProviderCallException error) {
            observability.externalRequest("provider", "http", "error");
            if (error.getCause() instanceof HttpTimeoutException) throw new ApiException(504, "Истекло время ожидания внешнего provider");
            if (error.getCause() instanceof InterruptedException) Thread.currentThread().interrupt();
            throw new ApiException(502, "Ошибка вызова внешнего provider: " + error.getCause().getClass().getSimpleName());
        } catch (IllegalArgumentException error) {
            observability.externalRequest("provider", "http", "error");
            throw new ApiException(502, "Ошибка вызова внешнего provider: " + error.getClass().getSimpleName());
        }
    }

    private static final class ProviderCallException extends RuntimeException {
        ProviderCallException(Exception cause) { super(cause); }
    }
}
