package com.ganchevdimitarg.catalog.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for the filename-driven schema registration surface. Fixture directories under
 * {@code src/test/resources} isolate one failure mode each.
 */
@Tag("unit")
class EventSchemaCatalogTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void should_loadEveryVersion_when_filesDeclareMatchingMetadata() {
        EventSchemaCatalog catalog = new EventSchemaCatalog(MAPPER, "event-schemas-fixture",
                List.of(ProductTopics.PRODUCT_CREATED));

        assertThat(catalog.versionsFor(ProductTopics.PRODUCT_CREATED)).containsExactly(1, 2);
        assertThat(catalog.schemaFor(ProductTopics.PRODUCT_CREATED, 2)).isNotNull();
    }

    @Test
    void should_failToLoad_when_aSchemaResourceIsMissing() {
        assertThatThrownBy(() -> new EventSchemaCatalog(MAPPER, "missing-event-schemas",
                ProductTopics.ALL))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("missing-event-schemas");
    }

    @Test
    void should_failToLoad_when_topicIsNotKnown() {
        assertThatThrownBy(() -> new EventSchemaCatalog(MAPPER, "event-schemas-unknown-topic",
                List.of(ProductTopics.PRODUCT_CREATED)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("unknown topic");
    }

    @Test
    void should_failToLoad_when_fileNameIsNotVersioned() {
        assertThatThrownBy(() -> new EventSchemaCatalog(MAPPER, "event-schemas-malformed-name",
                List.of(ProductTopics.PRODUCT_CREATED)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("filename must be");
    }

    @Test
    void should_failToLoad_when_idVersionDoesNotMatchFileName() {
        assertThatThrownBy(() -> new EventSchemaCatalog(MAPPER, "event-schemas-mismatch-id",
                List.of(ProductTopics.PRODUCT_CREATED)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("$id ending in :v1");
    }

    @Test
    void should_failToLoad_when_schemaVersionConstDoesNotMatchFileName() {
        assertThatThrownBy(() -> new EventSchemaCatalog(MAPPER, "event-schemas-mismatch-const",
                List.of(ProductTopics.PRODUCT_CREATED)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("schemaVersion.const");
    }

    @Test
    void should_failToLoad_when_aRequiredTopicHasNoSchema() {
        assertThatThrownBy(() -> new EventSchemaCatalog(MAPPER, "event-schemas-fixture",
                List.of(ProductTopics.PRODUCT_CREATED, ProductTopics.PRODUCT_UPDATED)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Missing JSON Schema")
                .hasMessageContaining(ProductTopics.PRODUCT_UPDATED);
    }

    @Test
    void should_rejectUnknownVersion_when_schemaForIsCalled() {
        EventSchemaCatalog catalog = new EventSchemaCatalog(MAPPER, "event-schemas-fixture",
                List.of(ProductTopics.PRODUCT_CREATED));

        assertThatThrownBy(() -> catalog.schemaFor(ProductTopics.PRODUCT_CREATED, 99))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("version 99")
                .hasMessageContaining("available versions: [1, 2]");
    }
}
