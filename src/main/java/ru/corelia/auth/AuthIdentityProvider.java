package ru.corelia.auth;

import java.util.Set;

/** Сообщает issuer и audiences, которые принимает выбранный provider авторизации. */
public interface AuthIdentityProvider {
    String issuer();
    Set<String> audiences();
}
