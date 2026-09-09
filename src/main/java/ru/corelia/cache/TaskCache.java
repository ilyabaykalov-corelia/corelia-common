package ru.corelia.cache;

import static ru.corelia.support.Json.*;

import io.lettuce.core.*;
import io.lettuce.core.api.StatefulRedisConnection;

import jakarta.annotation.PreDestroy;

import org.springframework.stereotype.Component;

import ru.corelia.auth.AuthContext;
import ru.corelia.config.CoreliaConfig;
import ru.corelia.support.LogJson;

import tools.jackson.databind.JsonNode;

import java.time.Duration;
import java.util.Set;
import java.util.concurrent.*;
import java.util.function.Supplier;

/** Необязательный Redis-кэш задач: свежий ответ, фоновое обновление, отключение при сбое. */
@Component
public class TaskCache {
    private static final String GENERATION = "corelia:task-cache:generation";
    private final CoreliaConfig config;
    private final UserCache local;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final Set<String> refreshing = ConcurrentHashMap.newKeySet();
    private final ConcurrentHashMap<String, CompletableFuture<JsonNode>> loading =
            new ConcurrentHashMap<>();
    private RedisClient client;
    private StatefulRedisConnection<String, String> connection;
    private volatile boolean disabled;

    public TaskCache(CoreliaConfig config, UserCache local) {
        this.config = config;
        this.local = local;
    }

    private synchronized StatefulRedisConnection<String, String> connection() {
        if (disabled || config.value("REDIS_URL").isEmpty()) return null;
        if (connection != null) return connection;
        try {
            RedisURI uri = RedisURI.create(config.value("REDIS_URL"));
            uri.setTimeout(Duration.ofMillis(config.number("REDIS_CONNECT_TIMEOUT_MS", 1000)));
            client = RedisClient.create(uri);
            client.setOptions(ClientOptions.builder().autoReconnect(false).build());
            connection = client.connect();
            return connection;
        } catch (RuntimeException error) {
            disable(error);
            return null;
        }
    }

    private synchronized void disable(RuntimeException error) {
        if (disabled) return;
        disabled = true;
        LogJson.info(
                "Кэш Redis отключен; gateway продолжит работу через сервисы Corelia без Redis",
                object(
                        "name", error.getClass().getSimpleName(),
                        "message", error.getMessage()));
    }

    public JsonNode get(
            AuthContext auth, String kind, JsonNode payload, Supplier<JsonNode> loader) {
        var redis = connection();
        if (redis == null) return loader.get();
        String key;
        try {
            String generation = redis.sync().get(GENERATION);
            // Ключ включает токен и адреса платформы: исключено смешивание пользователей и стендов.
            String namespace = UserCache.hash(config.dataspace() + "|" + config.bpmu());
            key =
                    "corelia:task-cache:"
                            + namespace
                            + ":"
                            + (generation == null ? "0" : generation)
                            + ":"
                            + UserCache.hash(auth.token())
                            + ":"
                            + kind
                            + ":"
                            + UserCache.hash(canonical(payload));
            String raw = redis.sync().get(key);
            if (raw != null) {
                JsonNode cached = parse(raw);
                if (number(cached, "freshUntil", 0) <= System.currentTimeMillis()
                        && refreshing.add(key)) {
                    executor.submit(
                            () -> {
                                try {
                                    save(key, loader.get());
                                } catch (RuntimeException error) {
                                    LogJson.info(
                                            "Redis task cache refresh failed",
                                            object(
                                                    "key", key,
                                                    "message", error.getMessage()));
                                } finally {
                                    refreshing.remove(key);
                                }
                            });
                }
                LogJson.info("Redis task cache hit", object("key", key, "state", number(cached, "freshUntil", 0) > System.currentTimeMillis() ? "fresh" : "stale"));
                return cached.path("data");
            }
        } catch (RuntimeException error) {
            disable(error);
            return loader.get();
        }
        var pending = new CompletableFuture<JsonNode>();
        var existing = loading.putIfAbsent(key, pending);
        if (existing != null) {
            try {
                LogJson.info("Redis task cache hit", object("key", key, "state", "loading"));
                return existing.join().deepCopy();
            } catch (CompletionException error) {
                if (error.getCause() instanceof RuntimeException cause) throw cause;
                throw error;
            }
        }
        try {
            JsonNode data = loader.get();
            save(key, data);
            pending.complete(data);
            LogJson.info("Redis task cache miss", object("key", key));
            return data;
        } catch (RuntimeException error) {
            pending.completeExceptionally(error);
            throw error;
        } finally {
            loading.remove(key, pending);
        }
    }

    private void save(String key, JsonNode data) {
        var redis = connection();
        if (redis == null) return;
        try {
            long fresh = config.number("REDIS_TASK_CACHE_FRESH_TTL_MS", 30000);
            long stale = config.number("REDIS_TASK_CACHE_STALE_TTL_MS", 300000);
            if (stale <= 0) return;
            redis.sync()
                    .psetex(
                            key,
                            stale,
                            write(
                                    object(
                                            "freshUntil",
                                            System.currentTimeMillis() + fresh,
                                            "data",
                                            data)));
        } catch (RuntimeException error) {
            disable(error);
        }
    }

    public void invalidate() {
        local.invalidate();
        var redis = connection();
        if (redis == null) return;
        // Смена поколения не позволяет запоздалому фоновому запросу вернуть старые данные в кэш.
        try {
            redis.sync().incr(GENERATION);
            LogJson.info("Redis task cache invalidated globally", object("generationKey", GENERATION));
        } catch (RuntimeException error) {
            disable(error);
        }
    }

    @PreDestroy
    public void close() {
        executor.shutdownNow();
        if (connection != null) connection.close();
        if (client != null) client.shutdown();
    }
}
