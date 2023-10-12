package com.concordeu.notification.mapper;

import com.concordeu.client.common.dto.NotificationDto;
import com.concordeu.notification.domain.Notification;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface MapStructMapper {

    Notification mapNotificationDtoToNotification (NotificationDto notificationDto);

    NotificationDto mapNotificationToNotificationDto (Notification notification);

}
