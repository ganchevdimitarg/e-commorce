package com.concordeu.notification.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.concordeu.notification.dao.NotificationDao;
import com.concordeu.notification.domain.Notification;
import com.concordeu.notification.dto.NotificationDto;
import com.concordeu.notification.mapper.MapStructMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationServiceImpl implements NotificationService {

	private final NotificationDao notificationDao;
	private final MapStructMapper mapper;

	@Override
	public String createNotification(NotificationDto notificationDto) {
		Notification notification = mapper.mapNotificationDtoToNotification(notificationDto);
		notification.setCreatedOn(LocalDateTime.now());
		notificationDao.saveAndFlush(notification);
		log.info("The notification has been created");
		return "The notification has been created";
	}

	@Override
	public String updateStatusOfNotification(final String notificationId) {
		Optional<Notification> notification = notificationDao.findById(notificationId);
		Notification newState;
		if (notification.isEmpty()) {
			return "Not found";
		} else {
			newState = notification.get();
		}
		newState.setViewed(!newState.isViewed());
		notificationDao.save(newState);
		log.info("The notification status has been updated");
		return "The notification status has been updated";

	}

	@Override
	public String deleteNotification(final String notificationId) {
		notificationDao.deleteById(notificationId);
		log.info("The notification status has been deleted");
		return "The notification status has been deleted";
	}

	@Override
	public List<Notification> getAll() {
		return notificationDao.findAll();
	}

	@Override
	public List<Notification> getNotificationsForUser(final String userId) {
		return notificationDao.findAllByUserId(userId);
	}
}
