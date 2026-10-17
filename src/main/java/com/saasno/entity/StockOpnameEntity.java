package com.saasno.entity;

import java.time.LocalDateTime;

import com.saasno.utility.generator.ID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

// Stok opname (penghitungan stok fisik) satu cabang: berlaku setelah disetujui orang lain.
// PENDING -> APPROVED | REJECTED | CANCELLED (tidak bisa kembali).
@Entity
@Table(schema = "public", name = "stock_opname")
public class StockOpnameEntity {

	public static final String PENDING = "PENDING";
	public static final String APPROVED = "APPROVED";
	public static final String REJECTED = "REJECTED";
	public static final String CANCELLED = "CANCELLED";

	@Id
	@Column(length = 64, nullable = false, updatable = false)
	private String id;

	// Diisi StockOpnameRepository.store(), format MMyyyydd + 3 digit urutan harian.
	@Column(length = 16, nullable = false, updatable = false, unique = true)
	private String serial;

	@Column(name = "branch_id", length = 64, nullable = false, updatable = false)
	private String branchId;

	@Column(length = 16, nullable = false)
	private String status = PENDING;

	@Column(length = 500, updatable = false)
	private String note;

	@Column(name = "requested_by", length = 64, nullable = false, updatable = false)
	private String requestedBy;

	@Column(nullable = false, updatable = false)
	private LocalDateTime registered;

	@Column(name = "decided_by", length = 64)
	private String decidedBy;

	@Column(name = "decided_at")
	private LocalDateTime decidedAt;

	@Column(name = "decision_note", length = 500)
	private String decisionNote;

	@Column(nullable = false)
	private LocalDateTime modificated;

	public StockOpnameEntity() {
		this.id = ID.uuid();
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

	public String getSerial() {
		return serial;
	}

	public void setSerial(String serial) {
		this.serial = serial;
	}

	public String getBranchId() {
		return branchId;
	}

	public void setBranchId(String branchId) {
		this.branchId = branchId;
	}

	public String getStatus() {
		return status;
	}

	public void setStatus(String status) {
		this.status = status;
	}

	public String getNote() {
		return note;
	}

	public void setNote(String note) {
		this.note = note;
	}

	public String getRequestedBy() {
		return requestedBy;
	}

	public void setRequestedBy(String requestedBy) {
		this.requestedBy = requestedBy;
	}

	public LocalDateTime getRegistered() {
		return registered;
	}

	public void setRegistered(LocalDateTime registered) {
		this.registered = registered;
	}

	public String getDecidedBy() {
		return decidedBy;
	}

	public void setDecidedBy(String decidedBy) {
		this.decidedBy = decidedBy;
	}

	public LocalDateTime getDecidedAt() {
		return decidedAt;
	}

	public void setDecidedAt(LocalDateTime decidedAt) {
		this.decidedAt = decidedAt;
	}

	public String getDecisionNote() {
		return decisionNote;
	}

	public void setDecisionNote(String decisionNote) {
		this.decisionNote = decisionNote;
	}

	public LocalDateTime getModificated() {
		return modificated;
	}

	public void setModificated(LocalDateTime modificated) {
		this.modificated = modificated;
	}

}
