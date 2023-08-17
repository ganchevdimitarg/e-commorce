package com.concordeu.notification.service;

import com.concordeu.notification.repositories.NotificationRepository;
import com.concordeu.notification.entities.Notification;
import com.concordeu.notification.dto.NotificationDTO;
import com.concordeu.notification.mapper.MapStructMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationServiceImpl implements NotificationService {

    private final NotificationRepository notificationRepository;
    private final MapStructMapper mapper;
    @Override
    public NotificationDTO createNotification(NotificationDTO notificationDto) {
        Notification notification = mapper.mapNotificationDtoToNotification(notificationDto);
        notification.setCreatedOn(LocalDateTime.now());
        NotificationDTO notificationResp = mapper.mapNotificationToNotificationDto(notificationRepository.saveAndFlush(notification));
        log.info("The notification has been created");
        return notificationResp;
    }
}
