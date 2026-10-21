package com.ganchevdimitarg.catalog.persistence;

import com.ganchevdimitarg.catalog.AbstractIntegrationTest;
import com.ganchevdimitarg.catalog.domain.OutboxEvent;
import com.ganchevdimitarg.catalog.repository.OutboxEventRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("integration")
@Transactional
class OutboxEventRepositoryIT extends AbstractIntegrationTest {

    private static final String TOPIC = "catalog.product.created";

    @Autowired
    OutboxEventRepository outboxEventRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    EntityManager entityManager;

    @Test
    void should_roundTripPayloadAndHeaders_asJsonb() {
        OutboxEvent saved = outboxEventRepository.saveAndFlush(event(
                "{\"productName\":\"mouse\"}", Map.of("eventType", "created"),
                Instant.now().minusSeconds(60), Instant.now(), null));
        entityManager.clear();

        OutboxEvent reloaded = outboxEventRepository.findById(saved.getId()).orElseThrow();

        // jsonb normalises whitespace (a space after each colon), so assert JSON content.
        assertThat(reloaded.getPayload()).isEqualToIgnoringWhitespace("{\"productName\":\"mouse\"}");
        assertThat(reloaded.getHeaders()).isEqualTo(Map.of("eventType", "created"));
        assertThat(jdbcTemplate.queryForObject(
                "select jsonb_typeof(payload) from outbox_events where id = ?",
                String.class, saved.getId())).isEqualTo("object");
        assertThat(jdbcTemplate.queryForObject(
                "select payload ->> 'productName' from outbox_events where id = ?",
                String.class, saved.getId())).isEqualTo("mouse");
        assertThat(jdbcTemplate.queryForObject(
                "select headers ->> 'eventType' from outbox_events where id = ?",
                String.class, saved.getId())).isEqualTo("created");
    }

    @Test
    void should_returnOnlyDueUnsentRows_when_claimingBatch() {
        Instant now = Instant.now();
        OutboxEvent due = outboxEventRepository.saveAndFlush(event(
                "{\"productName\":\"due\"}", Map.of("eventType", "created"),
                now.minusSeconds(3600), now.minusSeconds(7200), null));
        outboxEventRepository.saveAndFlush(event(
                "{\"productName\":\"future\"}", Map.of("eventType", "created"),
                now.plusSeconds(3600), now.minusSeconds(3600), null));
        outboxEventRepository.saveAndFlush(event(
                "{\"productName\":\"sent\"}", Map.of("eventType", "created"),
                now.minusSeconds(3600), now.minusSeconds(3600), now));
        entityManager.clear();

        List<OutboxEvent> claimed = outboxEventRepository.lockUnsentBatch(10);

        assertThat(claimed).extracting(OutboxEvent::getId).containsExactly(due.getId());
    }

    @Test
    void should_countUnsentRows() {
        Instant now = Instant.now();
        outboxEventRepository.saveAndFlush(event(
                "{\"productName\":\"unsent-one\"}", Map.of("eventType", "created"), now, now, null));
        outboxEventRepository.saveAndFlush(event(
                "{\"productName\":\"unsent-two\"}", Map.of("eventType", "created"), now, now, null));
        outboxEventRepository.saveAndFlush(event(
                "{\"productName\":\"sent\"}", Map.of("eventType", "created"), now, now, now));
        entityManager.flush();

        assertThat(outboxEventRepository.countBySentAtIsNullAndDeadLetteredAtIsNull()).isEqualTo(2);
    }

    @Test
    void should_excludeDeadLetteredRows_when_claimingAndCounting() {
        Instant now = Instant.now();
        OutboxEvent live = outboxEventRepository.saveAndFlush(event(
                "{\"productName\":\"live\"}", Map.of("eventType", "created"),
                now.minusSeconds(60), now.minusSeconds(60), null));
        OutboxEvent deadLettered = event(
                "{\"productName\":\"dead-lettered\"}", Map.of("eventType", "created"),
                now.minusSeconds(60), now.minusSeconds(60), null);
        deadLettered.setDeadLetteredAt(now);
        deadLettered.setDeadLetterReason("payload cannot be serialised");
        outboxEventRepository.saveAndFlush(deadLettered);
        entityManager.clear();

        assertThat(outboxEventRepository.lockUnsentBatch(10))
                .extracting(OutboxEvent::getId)
                .containsExactly(live.getId());
        assertThat(outboxEventRepository.countBySentAtIsNullAndDeadLetteredAtIsNull()).isEqualTo(1);
    }

    private OutboxEvent event(String payload, Map<String, String> headers,
                              Instant nextAttemptAt, Instant createdAt, Instant sentAt) {
        String aggregateId = UUID.randomUUID().toString();
        OutboxEvent event = new OutboxEvent();
        event.setAggregateType("Product");
        event.setAggregateId(aggregateId);
        event.setEventType("created");
        event.setTopic(TOPIC);
        event.setMessageKey(aggregateId);
        event.setPayload(payload);
        event.setHeaders(headers);
        event.setAttempts(0);
        event.setNextAttemptAt(nextAttemptAt);
        event.setCreatedAt(createdAt);
        event.setSentAt(sentAt);
        return event;
    }
}
