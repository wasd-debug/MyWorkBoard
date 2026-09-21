package com.salarytracker.ai.tool;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;

public final class ToolInputs {
    private ToolInputs() {
    }

    public static JsonNode object(JsonNode input) {
        if (input == null || input.isNull()) {
            return com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
        }
        if (!input.isObject()) throw new IllegalArgumentException("工具输入必须是 JSON object");
        return input;
    }

    public static String optionalDate(JsonNode input, String field) {
        String value = optionalText(input, field);
        if (value == null) return null;
        try {
            return LocalDate.parse(value).toString();
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException(field + " 必须是 ISO 日期 yyyy-MM-dd");
        }
    }

    public static String requiredText(JsonNode input, String field) {
        String value = optionalText(input, field);
        if (value == null) throw new IllegalArgumentException(field + " 必填");
        return value;
    }

    public static String optionalText(JsonNode input, String field) {
        JsonNode value = input.get(field);
        if (value == null || value.isNull()) return null;
        if (!value.isTextual()) throw new IllegalArgumentException(field + " 必须是字符串");
        String text = value.asText().trim();
        return text.isEmpty() ? null : text;
    }

    public static int integer(JsonNode input, String field, int defaultValue, int minimum, int maximum) {
        JsonNode value = input.get(field);
        if (value == null || value.isNull()) return defaultValue;
        if (!value.canConvertToInt()) throw new IllegalArgumentException(field + " 必须是整数");
        int number = value.intValue();
        if (number < minimum || number > maximum) {
            throw new IllegalArgumentException(field + " 必须在 " + minimum + " 到 " + maximum + " 之间");
        }
        return number;
    }
}
