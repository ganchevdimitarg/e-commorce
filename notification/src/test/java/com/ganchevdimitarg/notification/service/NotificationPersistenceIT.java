package com.ganchevdimitarg.notification.service;

import com.ganchevdimitarg.notification.AbstractIntegrationTest;
import com.ganchevdimitarg.notification.dao.NotificationDao;
import com.ganchevdimitarg.notification.domain.Notification;
import com.ganchevdimitarg.notification.dto.NotificationDto;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises the real schema (Flyway V1 + V2) on Postgres: persistence sets the audit
 * timestamps, the row is readable, and the soft-delete query filters deleted rows.
 */
class NotificationPersistenceIT extends AbstractIntegrationTest {

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private NotificationDao notificationDao;

    @Test
    void should_persistWithAuditColumns_when_notificationCreated() {
        notificationService.createNotification(
                new NotificationDto("user@test.com", "Subject", "A valid body over ten chars"));

        Notification saved = notificationDao.findAll().getFirst();
        assertThat(saved.getId()).isNotBlank();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
        assertThat(saved.getDeletedAt()).isNull();
        assertThat(saved.getMsgBody()).isEqualTo("A valid body over ten chars");
    }

    @Test
    void should_returnEmpty_when_findActiveByIdOnSoftDeletedRow() {
        notificationService.createNotification(
                new NotificationDto("gone@test.com", "Bye", "This body is long enough"));
        Notification saved = notificationDao.findAll().stream()
                .filter(n -> n.getRecipient().equals("gone@test.com"))
                .findFirst().orElseThrow();

        saved.setDeletedAt(java.time.LocalDateTime.now());
        notificationDao.saveAndFlush(saved);

        Optional<Notification> active = notificationDao.findActiveById(saved.getId());
        assertThat(active).isEmpty();
    }
}
