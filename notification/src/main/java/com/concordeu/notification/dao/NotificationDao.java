package com.concordeu.notification.dao;

import java.util.List;

import javax.transaction.Transactional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.concordeu.notification.domain.Notification;

public interface NotificationDao extends JpaRepository<Notification, String> {
	@Transactional
	List<Notification> findAllByUserId(String userId);
}
