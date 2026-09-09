package ru.corelia.http;

import static ru.corelia.support.Json.*;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.stereotype.Component;

import ru.corelia.auth.AuthContext;
import ru.corelia.config.CoreliaConfig;

import tools.jackson.databind.JsonNode;

import java.io.*;

/** Читает JSON с ограничением фактического размера, включая chunked-запросы. */
@Component
public class ApiRequest {
    private final long maxBodySize;

    public ApiRequest(CoreliaConfig config) {
        long megabytes = config.number("MAX_BODY_SIZE_MB", 35);
        if (megabytes < 1 || megabytes > 1024)
            throw new IllegalArgumentException("MAX_BODY_SIZE_MB должен быть от 1 до 1024");
        maxBodySize = megabytes * 1024 * 1024;
    }

    public AuthContext auth(HttpServletRequest request) {
        return (AuthContext) request.getAttribute(RequestSecurity.AUTH);
    }

    public JsonNode body(HttpServletRequest request) {
        if (request.getContentLengthLong() > maxBodySize) tooLarge();
        try {
            var out = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int count;
            var input = request.getInputStream();
            while ((count = input.read(buffer)) != -1) {
                if ((long) out.size() + count > maxBodySize) tooLarge();
                out.write(buffer, 0, count);
            }
            if (out.size() == 0) return object();
            JsonNode result;
            try {
                result = MAPPER.readTree(out.toByteArray());
            } catch (RuntimeException error) {
                throw new ApiException(400, "Некорректное тело JSON-запроса");
            }
            if (result == null || !result.isObject())
                throw new ApiException(400, "Тело JSON-запроса должно быть объектом");
            return result;
        } catch (IOException error) {
            throw new ApiException(400, "Не удалось прочитать тело запроса");
        }
    }

    private static void tooLarge() {
        throw new ApiException(413, "Превышен допустимый размер запроса");
    }
}
