package com.saasno.utility.dto.stock;

import java.time.LocalDateTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

// Permintaan hanya memakai branchId, note, dan items; sisanya diisi sistem.
@JsonIgnoreProperties(ignoreUnknown = true)
public class StockOpnameDTO {

	private String id;
	private String serial;
	private String branchId;
	private String branchName;
	private String status;
	private String note;
	private String requestedBy;
	private String requestedByName;
	private LocalDateTime registered;
	private String decidedBy;
	private String decidedByName;
	private LocalDateTime decidedAt;
	private String decisionNote;
	private List<StockOpnameItemDTO> items;

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

	public String getBranchName() {
		return branchName;
	}

	public void setBranchName(String branchName) {
		this.branchName = branchName;
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

	public String getRequestedByName() {
		return requestedByName;
	}

	public void setRequestedByName(String requestedByName) {
		this.requestedByName = requestedByName;
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

	public String getDecidedByName() {
		return decidedByName;
	}

	public void setDecidedByName(String decidedByName) {
		this.decidedByName = decidedByName;
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

	public List<StockOpnameItemDTO> getItems() {
		return items;
	}

	public void setItems(List<StockOpnameItemDTO> items) {
		this.items = items;
	}

}
