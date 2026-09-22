package ru.corelia.auth;

/** Сообщает адрес набора ключей выбранного provider-а авторизации. */
public interface AuthKeyProvider {
    String jwksUrl();
}
