package ru.corelia.cache;

import org.springframework.stereotype.Component;

import ru.corelia.auth.AuthContext;

import tools.jackson.databind.JsonNode;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.HexFormat;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/** Короткий локальный кэш, изолированный по токену; не разделяет видимость разных пользователей. */
@Component
public class UserCache {
    private record Entry(long expiresAt, JsonNode value) {}

    private final ConcurrentHashMap<String, Entry> entries = new ConcurrentHashMap<>();
    private final AtomicLong generation = new AtomicLong();

    public static String hash(String value) {
        try {
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException(error);
        }
    }

    public String key(AuthContext auth, String kind) {
        return hash(auth.token()) + ":" + kind;
    }

    public JsonNode get(AuthContext auth, String kind) {
        String key = key(auth, kind);
        var entry = entries.get(key);
        if (entry == null) return null;
        if (entry.expiresAt() <= System.currentTimeMillis()) {
            entries.remove(key, entry);
            return null;
        }
        return entry.value().deepCopy();
    }

    public void put(AuthContext auth, String kind, long ttl, JsonNode value) {
        if (ttl <= 0) return;
        if (entries.size() > 5000)
            entries.entrySet()
                    .removeIf(entry -> entry.getValue().expiresAt() <= System.currentTimeMillis());
        if (entries.size() > 10000) entries.clear();
        entries.put(key(auth, kind), new Entry(System.currentTimeMillis() + ttl, value.deepCopy()));
    }

    public JsonNode load(AuthContext auth, String kind, long ttl, Supplier<JsonNode> loader) {
        JsonNode cached = get(auth, kind);
        if (cached != null) return cached;
        long current = generation.get();
        JsonNode value = loader.get();
        synchronized (this) {
            if (current == generation.get()) put(auth, kind, ttl, value);
        }
        return value;
    }

    public synchronized void invalidate() {
        generation.incrementAndGet();
        entries.clear();
    }

    public void remove(AuthContext auth, String kind) {
        entries.remove(key(auth, kind));
    }
}
