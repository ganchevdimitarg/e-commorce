package com.ganchevdimitarg.catalog.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.Error;
import com.networknt.schema.InputFormat;
import com.networknt.schema.Schema;

import java.util.List;

/**
 * Validates product event payloads against the versioned JSON Schemas in
 * {@code classpath:event-schemas} before they are written to the outbox.
 *
 * <p>The event's own {@code schemaVersion} selects the schema, so adding
 * {@code <topic>.v2.json} is enough to evolve the contract without changing this class.
 * Loading happens in the constructor, so a missing, duplicate or inconsistent schema fails
 * application startup rather than the first event write. This is a registry-ready producer
 * contract, not an external registry; see ADR-010.
 */
public class EventSchemaValidator {

    private static final String SCHEMA_BASE_PATH = "event-schemas";

    private final ObjectMapper objectMapper;
    private final EventSchemaCatalog catalog;

    public EventSchemaValidator(ObjectMapper objectMapper) {
        this(objectMapper, new EventSchemaCatalog(objectMapper, SCHEMA_BASE_PATH, ProductTopics.ALL));
    }

    public EventSchemaValidator(ObjectMapper objectMapper, EventSchemaCatalog catalog) {
        this.objectMapper = objectMapper;
        this.catalog = catalog;
    }

    /**
     * Validates {@code event} against the schema selected by its {@code schemaVersion}.
     *
     * @throws IllegalStateException if the topic/version has no schema or the payload
     *                               violates it; the caller's transaction must roll back
     *                               rather than write an outbox row no consumer can rely on
     */
    public void validate(String topic, Object event) {
        String json = toJson(event);
        int version = schemaVersion(json, topic);
        Schema schema = catalog.schemaFor(topic, version);

        List<Error> errors = schema.validate(json, InputFormat.JSON);
        if (!errors.isEmpty()) {
            throw new IllegalStateException(
                    "Event for " + topic + " version " + version
                            + " violates its JSON Schema: " + errors);
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

    private int schemaVersion(String json, String topic) {
        try {
            JsonNode version = objectMapper.readTree(json).path("schemaVersion");
            if (!version.isInt()) {
                throw new IllegalStateException(
                        "Event for " + topic + " has no integer schemaVersion");
            }
            return version.intValue();
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(
                    "Failed to read schemaVersion for event on " + topic, e);
        }
    }
}
