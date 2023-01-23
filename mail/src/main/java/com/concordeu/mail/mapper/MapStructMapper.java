package com.concordeu.mail.mapper;

import com.concordeu.mail.domain.Mail;
import com.concordeu.mail.domain.MailList;
import com.concordeu.mail.dto.MailDto;
import com.concordeu.mail.dto.MailListDto;

import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface MapStructMapper {

    Mail mapNotificationDtoToNotification(MailDto mailDto);

    MailDto mapNotificationToNotificationDto(Mail mail);

	MailList mapMailListDtoToMailList(MailListDto mailListDto);

}
