package com.saasno.entity;

import java.time.LocalDateTime;

import com.saasno.utility.generator.ID;
import com.saasno.utility.picker.DownloadJobStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

@Entity
@Table(schema = "public", name = "download_job")
public class DownloadJobEntity {

	@Id
	@Column(length = 64, nullable = false, updatable = false)
	private String id;

	@Column(length = 64, nullable = false, updatable = false)
	private String type;

	@Column(length = 10, nullable = false, updatable = false)
	private String format;

	@Column(length = 32, nullable = false)
	@Enumerated(EnumType.STRING)
	private DownloadJobStatus status;

	@Column(length = 255)
	private String fileName;

	@Lob
	@Column(updatable = false)
	private String filterJson;

	@Column(length = 500)
	private String errorMessage;

	@Column(length = 64, nullable = false, updatable = false)
	private String requestedBy;

	@Column(nullable = false)
	private LocalDateTime modificated;

	@Column(nullable = false, updatable = false)
	private LocalDateTime registered;

	@PrePersist
	public void beforeInsert() {
		this.id = ID.uuid();
		this.status = DownloadJobStatus.QUEUED;
		this.registered = LocalDateTime.now();
		this.modificated = LocalDateTime.now();
	}

	@PreUpdate
	public void beforeUpdate() {
		this.modificated = LocalDateTime.now();
	}

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getType() {
		return type;
	}

	public void setType(String type) {
		this.type = type;
	}

	public String getFormat() {
		return format;
	}

	public void setFormat(String format) {
		this.format = format;
	}

	public DownloadJobStatus getStatus() {
		return status;
	}

	public void setStatus(DownloadJobStatus status) {
		this.status = status;
	}

	public String getFileName() {
		return fileName;
	}

	public void setFileName(String fileName) {
		this.fileName = fileName;
	}

	public String getFilterJson() {
		return filterJson;
	}

	public void setFilterJson(String filterJson) {
		this.filterJson = filterJson;
	}

	public String getErrorMessage() {
		return errorMessage;
	}

	public void setErrorMessage(String errorMessage) {
		this.errorMessage = errorMessage;
	}

	public String getRequestedBy() {
		return requestedBy;
	}

	public void setRequestedBy(String requestedBy) {
		this.requestedBy = requestedBy;
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
