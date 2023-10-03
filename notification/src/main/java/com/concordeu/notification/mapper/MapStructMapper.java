package com.concordeu.notification.mapper;

import com.concordeu.notification.domain.Notification;
import com.concordeu.notification.dto.NotificationDto;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface MapStructMapper {

    Notification mapNotificationDtoToNotification (NotificationDto notificationDto);

    NotificationDto mapNotificationToNotificationDto (Notification notification);

}
