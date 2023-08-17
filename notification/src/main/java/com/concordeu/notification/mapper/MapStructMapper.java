package com.concordeu.notification.mapper;

import com.concordeu.notification.entities.Notification;
import com.concordeu.notification.dto.NotificationDTO;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface MapStructMapper {

    Notification mapNotificationDtoToNotification (NotificationDTO notificationDto);

    NotificationDTO mapNotificationToNotificationDto (Notification notification);

}
