package ru.corelia.http;

/** Ошибка API с совместимым HTTP-статусом и сообщением для клиента. */
public class ApiException extends RuntimeException {
    private final int status;

    public ApiException(int status, String message) {
        super(message);
        this.status = status;
    }

    public int status() {
        return status;
    }
}
