package ru.corelia.document;

import static ru.corelia.support.Json.*;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.*;
import ru.corelia.http.ApiException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/** Контракт реквизитов КИД ОПС, независимый от хранилища. */
public final class KidOps {
    public static final String TYPE = "KID_OPS";
    public static final String NAME = "КИД ОПС";
    public static final List<String> FIELDS = List.of("contractNumber", "contractDate", "signingYear", "lastName", "firstName", "middleName", "snils");
    public static final Map<String, String> STATUSES = Map.of("CREATED", "Создан", "IN_WORK", "В работе", "STORED", "На хранении");
    private KidOps() {}
    public static ObjectNode validate(JsonNode attrs, boolean partial) {
        if (!attrs.isObject()) throw new ApiException(400, "Поле attributes должно быть объектом");
        for (String field : attrs.propertyNames()) if (!FIELDS.contains(field)) throw new ApiException(400, "Неизвестный атрибут: " + field);
        var result = object();
        for (String field : FIELDS) {
            JsonNode value = attrs.path(field);
            if (value.isMissingNode() && partial) continue;
            if (field.equals("middleName") && (value.isMissingNode() || value.isNull())) { result.put(field, ""); continue; }
            if (field.equals("signingYear")) {
                if (!value.isIntegralNumber() || value.asLong() < 1000 || value.asLong() > 9999)
                    throw new ApiException(400, "Год подписания должен быть целым числом из четырёх цифр");
                result.put(field, value.asInt()); continue;
            }
            if (!value.isTextual()) throw new ApiException(400, "Не заполнен или неверно задан атрибут: " + field);
            String raw = value.asString().trim();
            if (raw.isEmpty() && !field.equals("middleName")) throw new ApiException(400, "Не заполнен обязательный атрибут: " + field);
            int limit = switch(field) { case "contractNumber" -> 64; case "lastName" -> 40; case "firstName" -> 255; case "middleName" -> 256; case "snils" -> 14; default -> 10; };
            if (raw.codePointCount(0, raw.length()) > limit) throw new ApiException(400, "Превышена длина атрибута: " + field);
            if (field.equals("contractNumber") && !raw.matches("ОПС-[0-9]{3}-[0-9]{4}-[0-9]{7}")) throw new ApiException(400, "Номер договора должен иметь формат ОПС-ХХХ-ХХХХ-ХХХХХХХ");
            if (field.equals("snils") && !raw.matches("[0-9]{3}-[0-9]{3}-[0-9]{3} [0-9]{2}")) throw new ApiException(400, "СНИЛС должен иметь формат ХХХ-ХХХ-ХХХ ХХ");
            if (field.equals("contractDate")) {
                try { if (!raw.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) throw new DateTimeParseException("format", raw, 0); LocalDate.parse(raw); }
                catch(DateTimeParseException e) { throw new ApiException(400, "Некорректная дата договора"); }
            }
            result.put(field, raw);
        }
        return result;
    }
}
