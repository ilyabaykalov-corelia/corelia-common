package ru.corelia.auth;

/** Checks an opaque solution permission against the authenticated caller. */
public interface PermissionChecker {
    void require(String permission, AuthContext auth);
}
