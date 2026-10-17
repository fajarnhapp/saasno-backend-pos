package com.saasno.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.NamedQueries;
import jakarta.persistence.NamedQuery;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

@Entity
@Table(schema = "public", name = "notification_unread")
@NamedQueries({
		@NamedQuery(name = "NotificationUnreadEntity.incrementTotalByUserId", query = "UPDATE NotificationUnreadEntity n SET n.total = n.total + 1 WHERE n.userId = :userId")
})
public class NotificationUnreadEntity {

	@Id
	@Column(length = 64, nullable = false, updatable = false)
	private String userId;

	@Column(nullable = false)
	private Integer total;

	@Column(nullable = false)
	private LocalDateTime modificated;

	@Column(nullable = false, updatable = false)
	private LocalDateTime registered;

	@PrePersist
	public void beforeInsert() {
		this.registered = LocalDateTime.now();
		this.modificated = LocalDateTime.now();
	}

	@PreUpdate
	public void beforeUpdate() {
		this.modificated = LocalDateTime.now();
	}

	public NotificationUnreadEntity() {

	}

	public NotificationUnreadEntity(String userId, Integer total) {
		this.userId = userId;
		this.total = total;
	}

	public String getUserId() {
		return userId;
	}

	public void setUserId(String userId) {
		this.userId = userId;
	}

	public Integer getTotal() {
		return total;
	}

	public void setTotal(Integer total) {
		this.total = total;
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
