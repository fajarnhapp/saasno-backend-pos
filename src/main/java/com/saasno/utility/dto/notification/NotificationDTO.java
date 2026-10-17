package com.saasno.utility.dto.notification;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@JsonIgnoreProperties(ignoreUnknown = true)
public class NotificationDTO {

	private String id;

	@NotBlank(message = "User tidak boleh kosong")
	private String userId;

	@NotNull(message = "Pesan tidak boleh kosong")
	private NotificationMessageDTO notificationMessage;

	@NotNull(message = "Status tidak boleh kosong")
	@Min(value = 0, message = "Status must be at least 0")
	private Integer status = 0;

	private LocalDateTime modificated;

	private LocalDateTime registered;

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getUserId() {
		return userId;
	}

	public void setUserId(String userId) {
		this.userId = userId;
	}

	public NotificationMessageDTO getNotificationMessage() {
		return notificationMessage;
	}

	public void setNotificationMessage(NotificationMessageDTO notificationMessage) {
		this.notificationMessage = notificationMessage;
	}

	public Integer getStatus() {
		return status;
	}

	public void setStatus(Integer status) {
		this.status = status;
	}

	public LocalDateTime getModificated() {
		return modificated;
	}

	public void setModificated(LocalDateTime modificated) {
		this.modificated = modificated;
	}

	public LocalDateTime getRegistered() {
		return registered;
	}

	public void setRegistered(LocalDateTime registered) {
		this.registered = registered;
	}

}
