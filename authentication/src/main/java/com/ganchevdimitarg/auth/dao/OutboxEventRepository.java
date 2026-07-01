package com.ganchevdimitarg.auth.dao;

import com.ganchevdimitarg.auth.domain.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    List<OutboxEvent> findTop100ByStatusAndDeletedAtIsNullOrderByCreatedAtAsc(String status);
}
