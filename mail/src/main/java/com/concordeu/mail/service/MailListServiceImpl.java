package com.concordeu.mail.service;

import org.springframework.stereotype.Service;

import com.concordeu.mail.dao.MailListDao;
import com.concordeu.mail.domain.MailList;
import com.concordeu.mail.dto.MailListDto;
import com.concordeu.mail.mapper.MapStructMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class MailListServiceImpl implements MailListService {

	private final MailListDao mailListDao;

	private final MapStructMapper mapper;
	@Override
	public String save(final MailListDto mailListDto) {

		MailList mailList = mapper.mapMailListDtoToMailList(mailListDto);
		mailListDao.save(mailList);

		log.info(String.format("Saved to mail list user ID: %s", mailListDto.userId()));
		return String.format("Saved to mail list user ID: %s", mailListDto.userId());

	}

	@Override
	public String delete(final String userId) {
		MailList mailList = mailListDao.findByUserId(userId);
		if (mailList == null){
			return null;
		}
		mailList.setUserActive(false);
		mailListDao.save(mailList);
		log.info(String.format("Deactivated successfully user with ID: %s", userId));
		return String.format("Deactivated successfully user with ID: %s", userId);
	}

	@Override
	public String update(final MailListDto mailListDto){
		// TODO refactor
		MailList mailList = mapper.mapMailListDtoToMailList(mailListDto);
		mailListDao.save(mailList);

		log.info(String.format("Updated mail list user ID: %s", mailListDto.userId()));
		return String.format("Updated mail list user ID: %s", mailListDto.userId());
	}
}
