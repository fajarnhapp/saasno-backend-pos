package com.saasno.entity;

import java.time.LocalDateTime;

import com.saasno.utility.generator.ID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

@Entity
@Table(schema = "public", name = "notification")
public class NotificationEntity {

	@Id
	@Column(length = 64, nullable = false, updatable = false)
	private String id;

	@Column(length = 64, nullable = false, updatable = false)
	private String userId;

	@OneToOne(fetch = FetchType.EAGER)
	@JoinColumn(name = "notification_message_id", nullable = false)
	private NotificationMessageEntity notificationMessage;

	@Column(length = 1, nullable = false)
	private Integer status;

	@Column(nullable = false)
	private LocalDateTime modificated;

	@Column(nullable = false, updatable = false)
	private LocalDateTime registered;

	@PrePersist
	public void beforeInsert() {
		this.id = ID.uuid();
		this.status = 0;
		this.registered = LocalDateTime.now();
		this.modificated = LocalDateTime.now();
	}

	@PreUpdate
	public void beforeUpdate() {
		this.modificated = LocalDateTime.now();
	}

	public NotificationEntity() {

	}

	public NotificationEntity(String userId, NotificationMessageEntity notificationMessage) {
		this.userId = userId;
		this.notificationMessage = notificationMessage;
	}

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

	public NotificationMessageEntity getNotificationMessage() {
		return notificationMessage;
	}

	public void setNotificationMessage(NotificationMessageEntity notificationMessage) {
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
