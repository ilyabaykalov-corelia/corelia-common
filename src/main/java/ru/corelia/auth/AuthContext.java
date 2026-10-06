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
    public static final String INTERNAL_TASK_USERNAME_HEADER = "X-Corelia-Task-Username";
    public static final String INTERNAL_TASK_ROLES_HEADER = "X-Corelia-Task-Roles";

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
                    403, "В access token отсутствуют роли для поиска задач provider-а");
        return Map.of("X-Username", taskUsername, "X-Roles", String.join(",", roles));
    }

    /** Передает настроенный контекст задач только между Corelia-сервисами по mTLS. */
    public Map<String, String> internalTaskContextHeaders() {
        if (taskUsername.isEmpty() || roles.isEmpty()) return Map.of();
        return Map.of(
                INTERNAL_TASK_USERNAME_HEADER, taskUsername,
                INTERNAL_TASK_ROLES_HEADER, String.join(",", roles));
    }

    /** Восстанавливает настроенный контекст задач после проверки mTLS вызывающего сервиса. */
    public AuthContext withTaskContext(String forwardedTaskUsername, String forwardedRoles) {
        if (forwardedTaskUsername == null
                || forwardedTaskUsername.isBlank()
                || forwardedRoles == null
                || forwardedRoles.isBlank())
            return this;
        List<String> parsedRoles =
                java.util.Arrays.stream(forwardedRoles.split(","))
                        .map(String::trim)
                        .filter(role -> !role.isEmpty())
                        .toList();
        if (parsedRoles.isEmpty()) return this;
        return new AuthContext(
                token, id, login, fullName, email, parsedRoles, forwardedTaskUsername.trim());
    }

    @Override
    public String toString() {
        return "AuthContext[пользователь=" + login + "]";
    }
}
