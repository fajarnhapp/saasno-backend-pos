package com.saasno.utility.dto.stock;

import java.time.LocalDateTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

// Permintaan hanya memakai sourceBranchId, destinationBranchId, note, dan items; sisanya diisi sistem.
@JsonIgnoreProperties(ignoreUnknown = true)
public class StockTransferDTO {

	private String id;
	private String serial;
	private String sourceBranchId;
	private String sourceBranchName;
	private String destinationBranchId;
	private String destinationBranchName;
	private String status;
	private String note;
	private String requestedBy;
	private String requestedByName;
	private LocalDateTime registered;
	private String decidedBy;
	private String decidedByName;
	private LocalDateTime decidedAt;
	private String decisionNote;
	private List<StockTransferItemDTO> items;

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

	public String getSourceBranchId() {
		return sourceBranchId;
	}

	public void setSourceBranchId(String sourceBranchId) {
		this.sourceBranchId = sourceBranchId;
	}

	public String getSourceBranchName() {
		return sourceBranchName;
	}

	public void setSourceBranchName(String sourceBranchName) {
		this.sourceBranchName = sourceBranchName;
	}

	public String getDestinationBranchId() {
		return destinationBranchId;
	}

	public void setDestinationBranchId(String destinationBranchId) {
		this.destinationBranchId = destinationBranchId;
	}

	public String getDestinationBranchName() {
		return destinationBranchName;
	}

	public void setDestinationBranchName(String destinationBranchName) {
		this.destinationBranchName = destinationBranchName;
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

	public List<StockTransferItemDTO> getItems() {
		return items;
	}

	public void setItems(List<StockTransferItemDTO> items) {
		this.items = items;
	}

}
