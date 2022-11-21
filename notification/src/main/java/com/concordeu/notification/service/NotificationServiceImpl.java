package com.concordeu.notification.service;

import com.concordeu.notification.dao.NotificationDao;
import com.concordeu.notification.domain.Notification;
import com.concordeu.notification.dto.NotificationDto;
import com.concordeu.notification.mapper.MapStructMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationServiceImpl implements NotificationService {

    private final NotificationDao notificationDao;
    private final MapStructMapper mapper;
    @Override
    public NotificationDto createNotification(NotificationDto notificationDto) {
        Notification notification = mapper.mapNotificationDtoToNotification(notificationDto);
        return mapper.mapNotificationToNotificationDto(notificationDao.saveAndFlush(notification));
    }
}
