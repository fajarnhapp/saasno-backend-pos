package com.saasno.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.saasno.service.NotificationPreferenceService;
import com.saasno.service.NotificationService;
import com.saasno.utility.constant.Path;
import com.saasno.utility.dto.notification.NotificationDTO;
import com.saasno.utility.dto.notification.NotificationPreferenceDTO;
import com.saasno.utility.model.ResponseModel;
import com.saasno.utility.model.data.DataResult;

import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping(Path.API_NOTIFICATION)
public class NotificationController {

	@Autowired
	private NotificationService notificationService;

	@Autowired
	private NotificationPreferenceService preferenceService;

	@GetMapping("/me")
	public ResponseEntity<DataResult<NotificationDTO>> read(HttpServletRequest request) {
		return notificationService.read(request);
	}

	@GetMapping("/preference")
	public ResponseModel<List<NotificationPreferenceDTO>> preferences() {
		return preferenceService.read();
	}

	@PutMapping("/preference")
	public ResponseModel<List<NotificationPreferenceDTO>> updatePreference(@RequestBody NotificationPreferenceDTO request) {
		return preferenceService.update(request);
	}

	@PutMapping("/me")
	public ResponseModel<NotificationDTO> update(@RequestBody NotificationDTO notification) {
		return notificationService.update(notification);
	}

}
