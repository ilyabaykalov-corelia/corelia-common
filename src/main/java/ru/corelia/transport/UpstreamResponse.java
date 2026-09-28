package ru.corelia.transport;

import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.util.*;

import javax.net.ssl.SSLSession;

import ru.corelia.config.CoreliaConfig;
import ru.corelia.http.ApiException;

/** Ограничивает буферизуемые ответы внешних и внутренних HTTP-сервисов. */
public final class UpstreamResponse {
    private static final long BYTES_IN_MEGABYTE = 1024L * 1024L;

    private UpstreamResponse() {}

    public static long maxResponseBytes(CoreliaConfig config) {
        long megabytes = config.number("corelia.upstream.max-response-size-mb", 10);
        if (megabytes < 1 || megabytes > 1024)
            throw new IllegalArgumentException(
                    "CORELIA_UPSTREAM_MAX_RESPONSE_SIZE_MB должен быть от 1 до 1024");
        return megabytes * BYTES_IN_MEGABYTE;
    }

    public static HttpResponse<byte[]> read(HttpResponse<InputStream> response, long maxBytes)
            throws IOException {
        long contentLength = response.headers().firstValueAsLong("Content-Length").orElse(-1);
        if (contentLength > maxBytes) tooLarge();
        try (var input = response.body(); var output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) {
                if ((long) output.size() + count > maxBytes) tooLarge();
                output.write(buffer, 0, count);
            }
            return new BufferedResponse(response, output.toByteArray());
        }
    }

    private static void tooLarge() {
        throw new ApiException(502, "Превышен допустимый размер ответа внешнего сервиса");
    }

    private record BufferedResponse(HttpResponse<InputStream> source, byte[] body)
            implements HttpResponse<byte[]> {
        @Override public int statusCode() { return source.statusCode(); }
        @Override public HttpRequest request() { return source.request(); }
        @Override public Optional<HttpResponse<byte[]>> previousResponse() { return Optional.empty(); }
        @Override public HttpHeaders headers() { return source.headers(); }
        @Override public byte[] body() { return body; }
        @Override public Optional<SSLSession> sslSession() { return source.sslSession(); }
        @Override public URI uri() { return source.uri(); }
        @Override public HttpClient.Version version() { return source.version(); }
    }
}
