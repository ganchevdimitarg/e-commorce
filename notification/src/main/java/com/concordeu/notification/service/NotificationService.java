package com.concordeu.notification.service;

import java.util.List;

import com.concordeu.notification.domain.Notification;
import com.concordeu.notification.dto.NotificationDto;

public interface NotificationService {
	String createNotification(NotificationDto notification);

	String updateStatusOfNotification(String notificationId);

	String deleteNotification(String notificationId);

	List<Notification> getAll();

	List<Notification> getNotificationsForUser(String userId);
}
