package com.ganchevdimitarg.catalog.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.Error;
import com.networknt.schema.InputFormat;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Validates product event payloads against the versioned JSON Schemas in
 * {@code classpath:event-schemas} before they are written to the outbox.
 *
 * <p>This is a producer contract, not a schema registry: the schemas ship with the artifact
 * and the {@code schemaVersion} carried by each event selects the file suffix ({@code v1}
 * today). Loading happens in the constructor, so a missing schema fails application startup
 * rather than the first event write. See ADR-010.
 */
@Component
public class EventSchemaValidator {

    private static final String SCHEMA_BASE_PATH = "event-schemas";
    private static final String SCHEMA_VERSION_SUFFIX = ".v1.json";
    private static final List<String> TOPICS = List.of(
            ProductTopics.PRODUCT_CREATED,
            ProductTopics.PRODUCT_UPDATED,
            ProductTopics.PRODUCT_DELETED,
            ProductTopics.PRODUCT_CATEGORY_CHANGED);

    private final ObjectMapper objectMapper;
    private final Map<String, Schema> schemas;

    public EventSchemaValidator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.schemas = loadSchemas(SCHEMA_BASE_PATH);
    }

    /**
     * Validates {@code event} against the schema registered for {@code topic}.
     *
     * @throws IllegalStateException if the topic has no schema or the payload violates one;
     *                               the caller's transaction must roll back rather than write
     *                               an outbox row no consumer can rely on
     */
    public void validate(String topic, Object event) {
        Schema schema = schemas.get(topic);
        if (schema == null) {
            throw new IllegalStateException(
                    "No JSON Schema is registered for product event topic " + topic);
        }

        List<Error> errors = schema.validate(toJson(event), InputFormat.JSON);
        if (!errors.isEmpty()) {
            throw new IllegalStateException(
                    "Event for " + topic + " violates its JSON Schema: " + errors);
        }
    }

    /**
     * Serialises with the application's shared mapper so the validated JSON is the same
     * document the writer stores in the outbox.
     */
    private String toJson(Object event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialise event for JSON Schema validation", e);
        }
    }

    /**
     * Loads one schema per product topic. Package-private so tests can point at a base path
     * that does not exist and prove that a missing schema fails startup.
     */
    static Map<String, Schema> loadSchemas(String basePath) {
        SchemaRegistry registry = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12);
        Map<String, Schema> loaded = new LinkedHashMap<>();
        for (String topic : TOPICS) {
            String location = basePath + "/" + topic + SCHEMA_VERSION_SUFFIX;
            Resource resource = new ClassPathResource(location);
            if (!resource.exists()) {
                throw new IllegalStateException(
                        "Missing JSON Schema for product event topic " + topic + ": " + location);
            }
            try (InputStream schemaStream = resource.getInputStream()) {
                loaded.put(topic, registry.getSchema(schemaStream));
            } catch (IOException e) {
                throw new IllegalStateException(
                        "Failed to read JSON Schema for product event topic " + topic + ": " + location, e);
            }
        }
        return Map.copyOf(loaded);
    }
}
