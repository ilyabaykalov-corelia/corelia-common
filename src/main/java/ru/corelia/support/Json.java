package ru.corelia.support;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Работа с изменяемыми контрактами Platform V без потери неизвестных полей задач. */
public final class Json {
    public static final JsonMapper MAPPER = JsonMapper.builder().build();

    private Json() {}

    public static ObjectNode object(Object... pairs) {
        ObjectNode node = MAPPER.createObjectNode();
        for (int i = 0; i < pairs.length; i += 2)
            node.set((String) pairs[i], MAPPER.valueToTree(pairs[i + 1]));
        return node;
    }

    public static ArrayNode array(Collection<? extends JsonNode> values) {
        ArrayNode result = MAPPER.createArrayNode();
        values.forEach(result::add);
        return result;
    }

    public static List<JsonNode> list(JsonNode node) {
        if (node == null || !node.isArray()) return List.of();
        List<JsonNode> result = new ArrayList<>();
        node.forEach(result::add);
        return result;
    }

    public static String text(JsonNode node) {
        return node != null && node.isTextual() ? node.asString().trim() : "";
    }

    public static String text(JsonNode node, String field) {
        return node == null ? "" : text(node.path(field));
    }

    /** Возвращает строку ответа платформы без нормализации пользовательского ввода. */
    public static String storedText(JsonNode node, String field, String fallback) {
        JsonNode value = node.path(field);
        return value.isTextual() ? value.asString() : fallback;
    }

    public static String first(JsonNode node, String... fields) {
        for (String field : fields) {
            String value = text(node, field);
            if (!value.isEmpty()) return value;
        }
        return "";
    }

    public static String fallback(String value, String fallback) {
        return value.isEmpty() ? fallback : value;
    }

    public static long number(JsonNode node, String key, long fallback) {
        JsonNode value = node.path(key);
        if (value.isNumber()) return value.asLong();
        try {
            return (long) Double.parseDouble(text(value));
        } catch (NumberFormatException error) {
            return fallback;
        }
    }

    public static ObjectNode copy(JsonNode node) {
        return node != null && node.isObject() ? (ObjectNode) node.deepCopy() : object();
    }

    public static JsonNode parse(String value) {
        return MAPPER.readTree(value);
    }

    public static String write(Object value) {
        return MAPPER.writeValueAsString(value);
    }

    public static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8)
                .replace("+", "%20")
                .replace("%7E", "~");
    }

    public static JsonNode unwrap(JsonNode node) {
        return node.isObject() && node.has("value") ? node.path("value") : node;
    }

    public static String comparable(JsonNode node) {
        node = unwrap(node);
        if (node.isTextual()) return text(node);
        if (node.isNumber() || node.isBoolean()) return node.asString();
        return first(node, "name", "label", "displayValue");
    }

    public static String canonical(JsonNode node) {
        if (node.isObject()) {
            var sorted = new TreeMap<String, JsonNode>();
            node.properties()
                    .forEach(
                            entry ->
                                    sorted.put(entry.getKey(), parse(canonical(entry.getValue()))));
            return write(sorted);
        }
        if (node.isArray())
            return write(list(node).stream().map(item -> parse(canonical(item))).toList());
        return write(node);
    }
}
