package ru.corelia.support;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.net.URI;

import tools.jackson.databind.node.ObjectNode;

/**
 * Записывает структурированные диагностические события Corelia.
 *
 * <p>В полезной нагрузке нельзя передавать токен доступа, токен обновления, пароль или тело пользовательского
 * запроса. Для адресов внешних систем используется {@link #upstreamTarget(String)}, который
 * удаляет параметры запроса и фрагмент.
 */
public final class LogJson {
    private static final Logger LOG = LoggerFactory.getLogger("ru.corelia.json");

    private LogJson() {}

    /** Выводит одну структурированную строку: сообщение, пробел и компактный JSON. */
    public static void info(String message, Object payload) {
        LOG.info("{} {}", message, Json.write(withRequestId(payload)));
    }

    /** Выводит предупреждение в том же формате, чтобы событие оставалось пригодным для поиска. */
    public static void warn(String message, Object payload) {
        LOG.warn("{} {}", message, Json.write(withRequestId(payload)));
    }

    private static Object withRequestId(Object payload) {
        ObjectNode result =
                payload instanceof ObjectNode node
                        ? (ObjectNode) node.deepCopy()
                        : Json.object("payload", payload == null ? Json.object() : payload);
        String requestId = MDC.get("requestId");
        if (requestId != null && !requestId.isBlank() && !result.has("requestId"))
            result.put("requestId", requestId);
        return result;
    }

    /**
     * Возвращает origin и path upstream-адреса без query-параметров.
     * Query часто содержит идентификаторы, фильтры и другие данные пользователя.
     */
    public static String upstreamTarget(String value) {
        if (value == null || value.isBlank()) return "";
        try {
            URI uri = URI.create(value);
            String authority = uri.getRawAuthority();
            if (uri.getScheme() == null || authority == null || authority.isBlank())
                throw new IllegalArgumentException("Адрес upstream должен быть абсолютным");
            if (authority != null) {
                int userInfoEnd = authority.lastIndexOf('@');
                if (userInfoEnd >= 0) authority = authority.substring(userInfoEnd + 1);
            }
            String target = uri.getScheme() + "://" + authority;
            String path = uri.getRawPath();
            return target + (path == null || path.isEmpty() ? "/" : path);
        } catch (IllegalArgumentException error) {
            int query = value.indexOf('?');
            int fragment = value.indexOf('#');
            int end = query < 0 ? value.length() : query;
            if (fragment >= 0 && fragment < end) end = fragment;
            return value.substring(0, end);
        }
    }
}
