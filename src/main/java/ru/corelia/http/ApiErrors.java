package ru.corelia.http;

import static ru.corelia.support.Json.*;

import org.slf4j.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import ru.corelia.support.LogJson;

import tools.jackson.databind.JsonNode;

/** Сохраняет единый формат ошибок {message} и не раскрывает внутренние трассировки. */
@RestControllerAdvice
public class ApiErrors {
    private static final Logger LOG = LoggerFactory.getLogger(ApiErrors.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<JsonNode> api(ApiException error) {
        LogJson.info(
                "API request error",
                object("status", error.status(), "message", error.getMessage()));
        return ResponseEntity.status(error.status()).body(object("message", error.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<JsonNode> unexpected(Exception error) {
        LOG.error("Непредвиденная ошибка обработки API: {}", error.getClass().getName());
        LogJson.info(
                "API request error",
                object(
                        "status", 500,
                        "exception", error.getClass().getSimpleName(),
                        "message", "Внутренняя ошибка сервера"));
        return ResponseEntity.internalServerError()
                .body(object("message", "Внутренняя ошибка сервера"));
    }
}
