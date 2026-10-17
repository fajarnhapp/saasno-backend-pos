package com.saasno.entity;

import java.time.LocalDateTime;

import com.saasno.utility.generator.ID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table(schema = "public", name = "notification_message")
public class NotificationMessageEntity {

	@Id
	@Column(length = 64, nullable = false, updatable = false)
	private String id;

	@Column(nullable = false, columnDefinition = "TEXT")
	private String title = "Pesan baru " + LocalDateTime.now();

	@Column(nullable = false, columnDefinition = "TEXT")
	private String message;

	@Column(nullable = false, updatable = false)
	private LocalDateTime registered;

	// Jenis kejadian (mis. STOCK_TRANSFER_REQUEST) dan tautan tujuan di frontend (mis. /stock/transfer).
	@Column(name = "type", length = 32, nullable = false)
	private String type = "INFO";

	@Column(name = "link", length = 256)
	private String link;

	public NotificationMessageEntity() {

	}

	public NotificationMessageEntity(String title, String message) {
		this.title = title;
		this.message = message;
	}

	public NotificationMessageEntity(String type, String title, String message, String link) {
		this.type = type;
		this.title = title;
		this.message = message;
		this.link = link;
	}

	@PrePersist
	public void beforeInsert() {
		this.id = ID.uuid();
		this.registered = LocalDateTime.now();
	}

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getTitle() {
		return title;
	}

	public void setTitle(String title) {
		this.title = title;
	}

	public String getMessage() {
		return message;
	}

	public void setMessage(String message) {
		this.message = message;
	}

	public String getType() {
		return type;
	}

	public void setType(String type) {
		this.type = type;
	}

	public String getLink() {
		return link;
	}

	public void setLink(String link) {
		this.link = link;
	}

	public LocalDateTime getRegistered() {
		return registered;
	}

	public void setRegistered(LocalDateTime registered) {
		this.registered = registered;
	}

}
