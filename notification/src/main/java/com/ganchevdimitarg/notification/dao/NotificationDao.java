package com.ganchevdimitarg.notification.dao;

import com.ganchevdimitarg.notification.domain.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface NotificationDao extends JpaRepository<Notification, String> {

    @Query("SELECT n FROM Notification n WHERE n.id = :id AND n.deletedAt IS NULL")
    Optional<Notification> findActiveById(@Param("id") String id);
}
