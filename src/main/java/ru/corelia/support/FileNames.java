package ru.corelia.support;

/** Нейтральная нормализация имени пользовательского файла. */
public final class FileNames {
    private FileNames() {}

    public static String safe(String name) {
        String result = name.replaceAll("[\\\\/\\p{Cntrl}]", "_").trim();
        return result.isEmpty() ? "attachment.bin" : result;
    }
}
