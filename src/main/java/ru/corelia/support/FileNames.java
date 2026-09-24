package ru.corelia.support;

/** Нейтральная нормализация имени пользовательского файла. */
public final class FileNames {
    private FileNames() {}

    public static String safe(String name) {
        String result = name.replaceAll("[\\\\/\\x00]", "_").trim();
        return result.isEmpty() ? "attachment.bin" : result;
    }
}
