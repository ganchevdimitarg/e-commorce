package com.ganchevdimitarg.order.domain;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

/**
 * Audit columns shared by every persisted entity: DB-managed {@code created_at} /
 * {@code updated_at} and the {@code deleted_at} soft-delete marker. Hard deletes are
 * forbidden — entities are annotated {@code @SQLDelete}/{@code @SQLRestriction} to route
 * removals through {@code deleted_at}.
 */
@MappedSuperclass
@Getter
@Setter
public abstract class Auditable {

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;
}
