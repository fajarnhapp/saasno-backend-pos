package com.saasno.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.saasno.service.NotificationUnreadService;
import com.saasno.utility.constant.Path;
import com.saasno.utility.dto.notification.NotificationUnreadDTO;
import com.saasno.utility.model.ResponseModel;

@RestController
@RequestMapping(Path.API_NOTIFICATION_UNREAD)
public class NotificationUnreadController {

	@Autowired
	private NotificationUnreadService notificationUnreadService;

	@GetMapping("/me")
	public ResponseModel<NotificationUnreadDTO> find() {
		return notificationUnreadService.find();
	}

	@PutMapping("/me")
	public ResponseModel<NotificationUnreadDTO> update() {
		return notificationUnreadService.update();
	}

}
