package com.salarytracker.ai.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

public final class ToolSchemas {
    private ToolSchemas() {
    }

    public static ObjectNode object(ObjectMapper mapper) {
        ObjectNode schema = mapper.createObjectNode();
        schema.put("type", "object");
        schema.putObject("properties");
        schema.put("additionalProperties", false);
        return schema;
    }

    public static void stringProperty(ObjectNode schema, String name, String description, String format) {
        ObjectNode property = properties(schema).putObject(name);
        property.put("type", "string");
        property.put("description", description);
        if (format != null && !format.isBlank()) property.put("format", format);
    }

    public static void integerProperty(ObjectNode schema, String name, String description,
                                       int defaultValue, int minimum, int maximum) {
        ObjectNode property = properties(schema).putObject(name);
        property.put("type", "integer");
        property.put("description", description);
        property.put("default", defaultValue);
        property.put("minimum", minimum);
        property.put("maximum", maximum);
    }

    public static ObjectNode required(ObjectNode schema, String... names) {
        var required = schema.putArray("required");
        for (String name : names) required.add(name);
        return schema;
    }

    private static ObjectNode properties(ObjectNode schema) {
        JsonNode properties = schema.get("properties");
        if (!(properties instanceof ObjectNode object)) {
            throw new IllegalArgumentException("Schema 缺少 properties object");
        }
        return object;
    }
}
