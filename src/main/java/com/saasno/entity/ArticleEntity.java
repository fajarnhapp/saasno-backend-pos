package com.saasno.entity;

import java.time.LocalDateTime;

import com.saasno.utility.generator.ID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

// Artikel info/berita. DRAFT -> PUBLISHED -> ARCHIVED (boleh kembali ke DRAFT). Tayang di dashboard dan halaman
// Artikel selama PUBLISHED dan belum lewat expires_at. Isi disimpan sebagai teks biasa dengan format sederhana.
@Entity
@Table(schema = "public", name = "article")
public class ArticleEntity {

	public static final String DRAFT = "DRAFT";
	public static final String PUBLISHED = "PUBLISHED";
	public static final String ARCHIVED = "ARCHIVED";

	@Id
	@Column(name = "id", length = 64, nullable = false, updatable = false)
	private String id;

	@Column(name = "branch_id", length = 64, nullable = false, updatable = false)
	private String branchId;

	@Column(name = "title", length = 200, nullable = false)
	private String title;

	@Column(name = "summary", length = 500)
	private String summary;

	@Column(name = "content", nullable = false, columnDefinition = "TEXT")
	private String content;

	@Column(name = "status", length = 12, nullable = false)
	private String status;

	@Column(name = "pinned", nullable = false)
	private boolean pinned;

	@Column(name = "published_at")
	private LocalDateTime publishedAt;

	@Column(name = "expires_at")
	private LocalDateTime expiresAt;

	@Column(name = "created_by", length = 64, nullable = false, updatable = false)
	private String createdBy;

	@Column(name = "updated_by", length = 64, nullable = false)
	private String updatedBy;

	@Column(name = "registered", nullable = false, updatable = false)
	private LocalDateTime registered;

	@Column(name = "modificated", nullable = false)
	private LocalDateTime modificated;

	public ArticleEntity() {
		this.id = ID.uuid();
		this.status = DRAFT;
	}

	@PrePersist
	public void beforeInsert() {
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

	public String getBranchId() {
		return branchId;
	}

	public void setBranchId(String branchId) {
		this.branchId = branchId;
	}

	public String getTitle() {
		return title;
	}

	public void setTitle(String title) {
		this.title = title;
	}

	public String getSummary() {
		return summary;
	}

	public void setSummary(String summary) {
		this.summary = summary;
	}

	public String getContent() {
		return content;
	}

	public void setContent(String content) {
		this.content = content;
	}

	public String getStatus() {
		return status;
	}

	public void setStatus(String status) {
		this.status = status;
	}

	public boolean isPinned() {
		return pinned;
	}

	public void setPinned(boolean pinned) {
		this.pinned = pinned;
	}

	public LocalDateTime getPublishedAt() {
		return publishedAt;
	}

	public void setPublishedAt(LocalDateTime publishedAt) {
		this.publishedAt = publishedAt;
	}

	public LocalDateTime getExpiresAt() {
		return expiresAt;
	}

	public void setExpiresAt(LocalDateTime expiresAt) {
		this.expiresAt = expiresAt;
	}

	public String getCreatedBy() {
		return createdBy;
	}

	public void setCreatedBy(String createdBy) {
		this.createdBy = createdBy;
	}

	public String getUpdatedBy() {
		return updatedBy;
	}

	public void setUpdatedBy(String updatedBy) {
		this.updatedBy = updatedBy;
	}

	public LocalDateTime getRegistered() {
		return registered;
	}

	public void setRegistered(LocalDateTime registered) {
		this.registered = registered;
	}

	public LocalDateTime getModificated() {
		return modificated;
	}

	public void setModificated(LocalDateTime modificated) {
		this.modificated = modificated;
	}

}
