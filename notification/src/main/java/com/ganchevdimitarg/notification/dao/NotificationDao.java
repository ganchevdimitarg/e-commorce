package com.ganchevdimitarg.notification.dao;

import com.ganchevdimitarg.notification.domain.Notification;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationDao extends JpaRepository<Notification, String> {
}
