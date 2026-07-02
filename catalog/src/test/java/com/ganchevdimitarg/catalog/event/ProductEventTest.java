package com.ganchevdimitarg.catalog.event;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
class ProductEventTest {

    @Test
    void should_exposeIdentityFields_onCreatedEvent() {
        Instant now = Instant.now();
        ProductEvent event = new ProductEvent.ProductCreated("e1", "p1", "mouse", now);

        assertThat(event.eventId()).isEqualTo("e1");
        assertThat(event.productId()).isEqualTo("p1");
        assertThat(event.productName()).isEqualTo("mouse");
        assertThat(event.occurredAt()).isEqualTo(now);
    }
}
