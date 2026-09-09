package ru.corelia.http;

import static ru.corelia.support.Json.*;

import org.springframework.web.bind.annotation.*;

import ru.corelia.config.CoreliaConfig;

import tools.jackson.databind.JsonNode;

/** Проверка готовности локальной конфигурации; не запускает операции на платформе. */
@RestController
public class HealthController {
    private final CoreliaConfig config;

    public HealthController(CoreliaConfig config) {
        this.config = config;
    }

    @GetMapping({"/internal/v1/health", "/api/core/v1/health"})
    public JsonNode health() {
        return object(
                "status", "ok", "service", config.value("corelia.service"), "schemaVersion", 1);
    }

    @RequestMapping("/**")
    public JsonNode missing() {
        throw new ApiException(404, "Маршрут не найден");
    }
}
