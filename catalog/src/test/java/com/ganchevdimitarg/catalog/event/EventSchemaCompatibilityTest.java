package com.ganchevdimitarg.catalog.event;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.io.InputStream;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Enforces BACKWARD compatibility between consecutive versions of every event schema.
 *
 * <p>Inline text-block schemas pin each rule; the real fixture pair and every shipped
 * schema are checked through the same utility, so a future v2 cannot slip in with a
 * removal, a narrowed type or a newly required field.
 */
@Tag("unit")
class EventSchemaCompatibilityTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void should_findNoViolations_when_onlyAnOptionalPropertyIsAdded() throws Exception {
        JsonNode previous = schema("""
                {"type":"object","additionalProperties":false,"required":["name"],
                 "properties":{"name":{"type":"string"},
                               "schemaVersion":{"type":"integer","const":1}}}
                """);
        JsonNode next = schema("""
                {"type":"object","additionalProperties":false,"required":["name"],
                 "properties":{"name":{"type":"string"},
                               "colour":{"type":["string","null"]},
                               "schemaVersion":{"type":"integer","const":2}}}
                """);

        assertThat(JsonSchemaCompatibility.violations(previous, next)).isEmpty();
    }

    @Test
    void should_reportViolation_when_propertyIsRemoved() throws Exception {
        JsonNode previous = schema("""
                {"type":"object","properties":{"name":{"type":"string"}}}
                """);
        JsonNode next = schema("""
                {"type":"object","properties":{}}
                """);

        assertThat(JsonSchemaCompatibility.violations(previous, next))
                .anySatisfy(violation -> assertThat(violation).contains("property removed"));
    }

    @Test
    void should_reportViolation_when_propertyBecomesRequired() throws Exception {
        JsonNode previous = schema("""
                {"type":"object","required":["name"],
                 "properties":{"name":{"type":"string"},"colour":{"type":"string"}}}
                """);
        JsonNode next = schema("""
                {"type":"object","required":["name","colour"],
                 "properties":{"name":{"type":"string"},"colour":{"type":"string"}}}
                """);

        assertThat(JsonSchemaCompatibility.violations(previous, next))
                .anySatisfy(violation -> assertThat(violation).contains("newly required properties"));
    }

    @Test
    void should_reportViolation_when_typeIsNarrowed() throws Exception {
        JsonNode previous = schema("""
                {"type":"object","properties":{"name":{"type":["string","null"]}}}
                """);
        JsonNode next = schema("""
                {"type":"object","properties":{"name":{"type":"string"}}}
                """);

        assertThat(JsonSchemaCompatibility.violations(previous, next))
                .anySatisfy(violation -> assertThat(violation).contains("type narrowed"));
    }

    @Test
    void should_reportViolation_when_enumValueIsRemoved() throws Exception {
        JsonNode previous = schema("""
                {"type":"object","properties":{"status":{"type":"string","enum":["a","b"]}}}
                """);
        JsonNode next = schema("""
                {"type":"object","properties":{"status":{"type":"string","enum":["a"]}}}
                """);

        assertThat(JsonSchemaCompatibility.violations(previous, next))
                .anySatisfy(violation -> assertThat(violation).contains("allowed values removed"));
    }

    @Test
    void should_reportViolation_when_additionalPropertiesAreTightened() throws Exception {
        JsonNode previous = schema("""
                {"type":"object","additionalProperties":true}
                """);
        JsonNode next = schema("""
                {"type":"object","additionalProperties":false}
                """);

        assertThat(JsonSchemaCompatibility.violations(previous, next))
                .anySatisfy(violation -> assertThat(violation).contains("additionalProperties tightened"));
    }

    @Test
    void should_findNoViolations_when_fixtureVersionsAddAnOptionalProperty() throws Exception {
        Map<Integer, JsonNode> versions = loadVersions("event-schemas-fixture",
                ProductTopics.PRODUCT_CREATED);

        assertThat(versions.keySet()).containsExactly(1, 2);
        assertThat(JsonSchemaCompatibility.violations(versions.get(1), versions.get(2))).isEmpty();
    }

    @Test
    void should_findNoViolations_when_everyShippedSchemaIsComparedWithItself() throws Exception {
        Resource[] resources = new PathMatchingResourcePatternResolver()
                .getResources("classpath*:event-schemas/*.json");

        assertThat(resources).isNotEmpty();
        for (Resource resource : resources) {
            try (InputStream input = resource.getInputStream()) {
                JsonNode schema = MAPPER.readTree(input);
                assertThat(JsonSchemaCompatibility.violations(schema, schema))
                        .as(resource.getFilename())
                        .isEmpty();
            }
        }
    }

    private static JsonNode schema(String json) throws Exception {
        return MAPPER.readTree(json);
    }

    private static Map<Integer, JsonNode> loadVersions(String basePath, String topic)
            throws Exception {
        Resource[] resources = new PathMatchingResourcePatternResolver()
                .getResources("classpath*:" + basePath + "/*.json");
        Pattern fileName = Pattern.compile(Pattern.quote(topic) + "\\.v([1-9][0-9]*)\\.json");
        NavigableMap<Integer, JsonNode> versions = new TreeMap<>();
        for (Resource resource : resources) {
            String name = resource.getFilename();
            Matcher matcher = name == null ? null : fileName.matcher(name);
            if (matcher != null && matcher.matches()) {
                try (InputStream input = resource.getInputStream()) {
                    versions.put(Integer.parseInt(matcher.group(1)), MAPPER.readTree(input));
                }
            }
        }
        return versions;
    }
}
