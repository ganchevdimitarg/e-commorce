package com.ganchevdimitarg.catalog.event;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Structural BACKWARD compatibility checker for the shipped JSON Schemas.
 *
 * <p>BACKWARD means a payload written under the previous schema must still validate against
 * the next one. The checks therefore reject property removal, narrowed types, enum values
 * removed, newly required properties, changed constants and tightened
 * {@code additionalProperties}. Adding an optional property is allowed. {@code $id} and
 * {@code properties.schemaVersion.const} are expected to change between versions and are
 * exempt.
 *
 * <p>This is a test utility, not a general-purpose schema comparator: the shipped schemas
 * are flat Draft 2020-12 objects, so the recursion only needs {@code properties} and
 * {@code items}.
 */
final class JsonSchemaCompatibility {

    private JsonSchemaCompatibility() {
    }

    static List<String> violations(JsonNode previous, JsonNode next) {
        List<String> violations = new ArrayList<>();
        compareObject(previous, next, "$", violations);
        return List.copyOf(violations);
    }

    private static void compareObject(JsonNode previous, JsonNode next, String path,
                                      List<String> violations) {
        compareTypes(previous, next, path, violations);
        compareRequired(previous, next, path, violations);
        compareAdditionalProperties(previous, next, path, violations);

        JsonNode previousProperties = previous.path("properties");
        JsonNode nextProperties = next.path("properties");
        previousProperties.fieldNames().forEachRemaining(name -> {
            if (!nextProperties.has(name)) {
                violations.add(path + "." + name + ": property removed");
            } else {
                compareProperty(previousProperties.get(name), nextProperties.get(name),
                        path + "." + name, violations);
            }
        });

        if (previous.has("items") || next.has("items")) {
            compareProperty(previous.path("items"), next.path("items"), path + "[]", violations);
        }
    }

    private static void compareProperty(JsonNode previous, JsonNode next, String path,
                                        List<String> violations) {
        compareTypes(previous, next, path, violations);
        if (!path.endsWith(".schemaVersion")) {
            compareAllowedValues(previous, next, path, violations);
        }
        if (previous.has("properties") || next.has("properties")) {
            compareObject(previous, next, path, violations);
        }
        if (previous.has("items") || next.has("items")) {
            compareProperty(previous.path("items"), next.path("items"), path + "[]", violations);
        }
    }

    private static void compareTypes(JsonNode previous, JsonNode next, String path,
                                     List<String> violations) {
        Set<String> previousTypes = types(previous);
        Set<String> nextTypes = types(next);
        if (previousTypes.contains("*")) {
            if (!nextTypes.contains("*")) {
                violations.add(path + ": type narrowed from unrestricted to " + nextTypes);
            }
            return;
        }
        if (nextTypes.contains("*")) {
            return;
        }
        if (!nextTypes.containsAll(previousTypes)) {
            violations.add(path + ": type narrowed from " + previousTypes + " to " + nextTypes);
        }
    }

    private static void compareRequired(JsonNode previous, JsonNode next, String path,
                                        List<String> violations) {
        Set<String> newlyRequired = new TreeSet<>(required(next));
        newlyRequired.removeAll(required(previous));
        if (!newlyRequired.isEmpty()) {
            violations.add(path + ": newly required properties " + newlyRequired);
        }
    }

    private static void compareAdditionalProperties(JsonNode previous, JsonNode next, String path,
                                                    List<String> violations) {
        if (allowsAdditional(previous) && !allowsAdditional(next)) {
            violations.add(path + ": additionalProperties tightened");
        }
    }

    private static void compareAllowedValues(JsonNode previous, JsonNode next, String path,
                                             List<String> violations) {
        Set<String> previousValues = allowedValues(previous);
        if (previousValues == null) {
            return;
        }
        Set<String> nextValues = allowedValues(next);
        if (nextValues == null) {
            return;
        }
        Set<String> removed = new TreeSet<>(previousValues);
        removed.removeAll(nextValues);
        if (!removed.isEmpty()) {
            violations.add(path + ": allowed values removed " + removed);
        }
    }

    private static Set<String> types(JsonNode schema) {
        JsonNode type = schema.path("type");
        if (type.isTextual()) {
            return Set.of(type.textValue());
        }
        if (type.isArray()) {
            Set<String> types = new TreeSet<>();
            type.forEach(value -> types.add(value.asText()));
            return types;
        }
        return Set.of("*");
    }

    private static Set<String> required(JsonNode schema) {
        JsonNode required = schema.path("required");
        Set<String> names = new TreeSet<>();
        if (required.isArray()) {
            required.forEach(name -> names.add(name.asText()));
        }
        return names;
    }

    private static boolean allowsAdditional(JsonNode schema) {
        JsonNode additional = schema.path("additionalProperties");
        return !additional.isBoolean() || additional.booleanValue();
    }

    /**
     * Returns the values the schema accepts, or {@code null} when it is unbounded (no
     * {@code const} and no {@code enum}).
     */
    private static Set<String> allowedValues(JsonNode schema) {
        if (schema.has("const")) {
            return Set.of(schema.get("const").toString());
        }
        JsonNode enumNode = schema.path("enum");
        if (enumNode.isArray()) {
            Set<String> values = new TreeSet<>();
            enumNode.forEach(value -> values.add(value.toString()));
            return values;
        }
        return null;
    }
}
