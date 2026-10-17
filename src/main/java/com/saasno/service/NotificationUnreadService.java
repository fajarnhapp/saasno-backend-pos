package com.saasno.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.saasno.entity.NotificationUnreadEntity;
import com.saasno.repository.NotificationUnreadRepository;
import com.saasno.utility.SessionUtility;
import com.saasno.utility.dto.notification.NotificationUnreadDTO;
import com.saasno.utility.exception.ApiException;
import com.saasno.utility.mapper.NotificationUnreadMapper;
import com.saasno.utility.model.ResponseModel;

@Service
public class NotificationUnreadService {

	@Autowired
	private NotificationUnreadRepository notificationUnreadRepository;

	@Autowired
	private NotificationUnreadMapper notificationUnreadMapper;

	@Autowired
	private SessionUtility sessionUtility;

	@Transactional(readOnly = true)
	public ResponseModel<NotificationUnreadDTO> find() {
		NotificationUnreadEntity existingNotificationUnread = notificationUnreadRepository
				.find(sessionUtility.getUserId());

		// User yang belum pernah menerima notifikasi belum punya baris penghitung: dianggap 0.
		if (existingNotificationUnread == null) {
			return new ResponseModel<NotificationUnreadDTO>().success(empty());
		}

		return new ResponseModel<NotificationUnreadDTO>()
				.success(notificationUnreadMapper.dto(existingNotificationUnread));
	}

	@Transactional
	public ResponseModel<NotificationUnreadDTO> update() {
		NotificationUnreadEntity existingNotificationUnread = notificationUnreadRepository
				.find(sessionUtility.getUserId());

		if (existingNotificationUnread == null) {
			return new ResponseModel<NotificationUnreadDTO>().success(empty());
		}

		existingNotificationUnread.setTotal(0);
		notificationUnreadRepository.update(existingNotificationUnread);
		return new ResponseModel<NotificationUnreadDTO>()
				.success(notificationUnreadMapper.dto(existingNotificationUnread));
	}

	private NotificationUnreadDTO empty() {
		NotificationUnreadDTO dto = new NotificationUnreadDTO();
		dto.setUserId(sessionUtility.getUserId());
		dto.setTotal(0);
		return dto;
	}

}
