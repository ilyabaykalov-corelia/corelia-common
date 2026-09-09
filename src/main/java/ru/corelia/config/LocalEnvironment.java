package ru.corelia.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/** Читает .env рабочего каталога; системные параметры и окружение имеют приоритет. */
public final class LocalEnvironment {
    private static final Pattern ASSIGNMENT =
            Pattern.compile("^([A-Za-z_][A-Za-z0-9_]*)\\s*=\\s*(.*)$");

    private LocalEnvironment() {}

    public static Map<String, Object> load() {
        return load(Path.of(".env"));
    }

    public static Map<String, Object> load(Path path) {
        Map<String, Object> values = new LinkedHashMap<>();
        if (!Files.exists(path)) return values;
        try {
            for (String line : Files.readAllLines(path)) {
                var match = ASSIGNMENT.matcher(line.trim());
                if (!match.matches()) continue;
                String value = match.group(2).trim();
                if (value.length() >= 2
                        && ((value.startsWith("\"") && value.endsWith("\""))
                                || (value.startsWith("'") && value.endsWith("'")))) {
                    value = value.substring(1, value.length() - 1);
                }
                values.putIfAbsent(match.group(1), value);
            }
            return values;
        } catch (IOException error) {
            throw new IllegalStateException("Не удалось прочитать локальный файл окружения", error);
        }
    }
}
