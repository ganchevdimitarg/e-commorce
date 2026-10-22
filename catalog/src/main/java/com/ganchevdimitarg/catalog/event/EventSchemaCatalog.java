package com.ganchevdimitarg.catalog.event;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Loads every {@code <topic>.v<N>.json} schema shipped under a classpath directory and keys
 * it by topic and version.
 *
 * <p>The filename is the registration surface: a schema whose {@code $id} or
 * {@code properties.schemaVersion.const} disagrees with its {@code v<N>} suffix is rejected
 * at startup rather than silently validating against the wrong contract. Duplicate versions,
 * unknown topics and missing topics also fail startup. This is the in-artifact stand-in for
 * a schema registry; ADR-010 records the registry-ready naming convention.
 */
final class EventSchemaCatalog {

    private static final Pattern FILE_NAME = Pattern.compile("^(.+)\\.v([1-9][0-9]*)\\.json$");

    private final Map<String, NavigableMap<Integer, Schema>> schemas;

    EventSchemaCatalog(ObjectMapper objectMapper, String basePath, Collection<String> topics) {
        this(objectMapper, new PathMatchingResourcePatternResolver(), basePath, topics);
    }

    EventSchemaCatalog(ObjectMapper objectMapper, ResourcePatternResolver resolver,
                       String basePath, Collection<String> topics) {
        this.schemas = load(objectMapper, resolver, basePath, topics);
    }

    Schema schemaFor(String topic, int version) {
        NavigableMap<Integer, Schema> versions = schemas.get(topic);
        if (versions == null) {
            throw new IllegalStateException(
                    "No JSON Schema is registered for product event topic " + topic);
        }
        Schema schema = versions.get(version);
        if (schema == null) {
            throw new IllegalStateException("No JSON Schema is registered for product event topic "
                    + topic + " version " + version + "; available versions: " + versions.keySet());
        }
        return schema;
    }

    Set<Integer> versionsFor(String topic) {
        NavigableMap<Integer, Schema> versions = schemas.get(topic);
        if (versions == null) {
            throw new IllegalStateException(
                    "No JSON Schema is registered for product event topic " + topic);
        }
        return Collections.unmodifiableSet(versions.keySet());
    }

    private static Map<String, NavigableMap<Integer, Schema>> load(
            ObjectMapper objectMapper, ResourcePatternResolver resolver,
            String basePath, Collection<String> topics) {
        Resource[] resources;
        try {
            resources = resolver.getResources("classpath*:" + basePath + "/*.json");
        } catch (IOException e) {
            throw new IllegalStateException("Failed to scan JSON Schemas under " + basePath, e);
        }
        if (resources.length == 0) {
            throw new IllegalStateException("No JSON Schemas found under " + basePath);
        }

        SchemaRegistry registry = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12);
        Map<String, NavigableMap<Integer, Schema>> loaded = new LinkedHashMap<>();
        for (Resource resource : resources) {
            String location = basePath + "/" + resource.getFilename();
            Matcher matcher = matcherFor(resource, location);
            String topic = matcher.group(1);
            int version = Integer.parseInt(matcher.group(2));
            if (!topics.contains(topic)) {
                throw new IllegalStateException(
                        "JSON Schema " + location + " is registered for unknown topic " + topic);
            }

            byte[] schemaBytes = read(resource, location);
            validateMetadata(objectMapper, schemaBytes, topic, version, location);

            NavigableMap<Integer, Schema> versions =
                    loaded.computeIfAbsent(topic, ignored -> new TreeMap<>());
            if (versions.containsKey(version)) {
                throw new IllegalStateException(
                        "Duplicate JSON Schema for product event topic " + topic + " version " + version);
            }
            try (InputStream schemaStream = new ByteArrayInputStream(schemaBytes)) {
                versions.put(version, registry.getSchema(schemaStream));
            } catch (IOException e) {
                throw new IllegalStateException(
                        "Failed to read JSON Schema " + location, e);
            }
        }

        for (String topic : topics) {
            if (!loaded.containsKey(topic)) {
                throw new IllegalStateException(
                        "Missing JSON Schema for product event topic " + topic
                                + " under " + basePath);
            }
        }
        return deepUnmodifiable(loaded);
    }

    private static Matcher matcherFor(Resource resource, String location) {
        String fileName = resource.getFilename();
        Matcher matcher = fileName == null ? null : FILE_NAME.matcher(fileName);
        if (matcher == null || !matcher.matches()) {
            throw new IllegalStateException(
                    "JSON Schema filename must be <topic>.v<N>.json: " + location);
        }
        return matcher;
    }

    private static byte[] read(Resource resource, String location) {
        try (InputStream input = resource.getInputStream()) {
            return input.readAllBytes();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read JSON Schema " + location, e);
        }
    }

    private static void validateMetadata(ObjectMapper objectMapper, byte[] schemaBytes,
                                         String topic, int version, String location) {
        JsonNode schema;
        try {
            schema = objectMapper.readTree(schemaBytes);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to parse JSON Schema " + location, e);
        }

        String id = schema.path("$id").asText("");
        if (!id.endsWith(":v" + version)) {
            throw new IllegalStateException("JSON Schema " + location
                    + " must declare $id ending in :v" + version + " but was '" + id + "'");
        }
        JsonNode constVersion = schema.path("properties").path("schemaVersion").path("const");
        if (!constVersion.isInt() || constVersion.intValue() != version) {
            throw new IllegalStateException("JSON Schema " + location
                    + " must declare properties.schemaVersion.const = " + version
                    + " but was " + constVersion);
        }
    }

    private static Map<String, NavigableMap<Integer, Schema>> deepUnmodifiable(
            Map<String, NavigableMap<Integer, Schema>> loaded) {
        Map<String, NavigableMap<Integer, Schema>> result = new LinkedHashMap<>();
        loaded.forEach((topic, versions) ->
                result.put(topic, Collections.unmodifiableNavigableMap(versions)));
        return Collections.unmodifiableMap(result);
    }
}
