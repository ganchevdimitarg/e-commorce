package com.concordeu.notification.dao;

import com.concordeu.notification.domain.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

public interface NotificationDao extends JpaRepository<Notification, String> {
}
