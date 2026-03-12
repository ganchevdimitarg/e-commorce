package com.ganchevdimitarg.notification.service;

import com.ganchevdimitarg.notification.dao.NotificationDao;
import com.ganchevdimitarg.notification.domain.Notification;
import com.ganchevdimitarg.notification.dto.NotificationDto;
import com.ganchevdimitarg.notification.mapper.MapStructMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationServiceImpl implements NotificationService {

    private final NotificationDao notificationDao;
    private final MapStructMapper mapper;
    @Override
    public NotificationDto createNotification(NotificationDto notificationDto) {
        Notification notification = mapper.mapNotificationDtoToNotification(notificationDto);
        notification.setCreatedOn(LocalDateTime.now());
        NotificationDto notificationResp = mapper.mapNotificationToNotificationDto(notificationDao.saveAndFlush(notification));
        log.info("The notification has been created");
        return notificationResp;
    }
}
