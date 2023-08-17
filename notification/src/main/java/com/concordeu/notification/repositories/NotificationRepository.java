package com.concordeu.notification.repositories;

import com.concordeu.notification.entities.Notification;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepository extends JpaRepository<Notification, String> {
}
