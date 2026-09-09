package ru.corelia.auth;

import static ru.corelia.support.Json.*;

import ru.corelia.http.ApiException;

import tools.jackson.databind.node.ObjectNode;

import java.util.List;
import java.util.Map;

/** Неизменяемый контекст проверенного запроса, пригодный для фонового обновления кэша. */
public record AuthContext(
        String token,
        String id,
        String login,
        String fullName,
        String email,
        List<String> roles,
        String taskUsername) {
    public AuthContext {
        roles = List.copyOf(roles);
    }

    public String authorization() {
        return "Bearer " + token;
    }

    public ObjectNode user() {
        var user = object("id", id, "login", login, "fullName", fullName);
        if (!email.isEmpty()) user.put("email", email);
        return user;
    }

    public Map<String, String> taskHeaders() {
        if (taskUsername.isEmpty())
            throw new ApiException(401, "В Keycloak access token отсутствует имя пользователя");
        if (roles.isEmpty())
            throw new ApiException(
                    403, "В Keycloak access token отсутствуют роли для вызова BPMU Task List");
        return Map.of("X-Username", taskUsername, "X-Roles", String.join(",", roles));
    }

    @Override
    public String toString() {
        return "AuthContext[пользователь=" + login + "]";
    }
}
