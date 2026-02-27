package com.ganchevdimitarg.notification.mapper;

import com.ganchevdimitarg.notification.domain.Notification;
import com.ganchevdimitarg.notification.dto.NotificationDto;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface MapStructMapper {

    Notification mapNotificationDtoToNotification (NotificationDto notificationDto);

    NotificationDto mapNotificationToNotificationDto (Notification notification);

}
