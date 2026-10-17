package com.saasno.utility.dto.finance;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

// Biaya operasional. Mencatat biaya kas: branchId (user global), category, description, amount, receiptNo, reason, approvedBy.
// Pemakaian stok sendiri: branchId, productId, quantity, unit, category, description (opsional), reason, approvedBy.
// approvedBy = nama pemberi persetujuan lisan (opsional, keterangan saja). warnings dan pettyCashBalance diisi sistem.
@JsonIgnoreProperties(ignoreUnknown = true)
public class ExpenseDTO {

	private String id;
	private String serial;
	private String branchId;
	private String branchName;
	private String kind;
	private String category;
	private String description;
	private BigDecimal amount;
	private String receiptNo;
	private String reason;
	private String approvedBy;
	private String productId;
	private String productSerial;
	private String productName;
	private String unit;
	private BigDecimal quantity;
	private BigDecimal unitCost;
	private Boolean overThreshold;
	private String status;
	private String createdBy;
	private String createdByName;
	private LocalDateTime registered;
	private String voidedBy;
	private String voidedByName;
	private LocalDateTime voidedAt;
	private String voidReason;
	private List<String> warnings;
	private BigDecimal pettyCashBalance;

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

	public String getKind() {
		return kind;
	}

	public void setKind(String kind) {
		this.kind = kind;
	}

	public String getCategory() {
		return category;
	}

	public void setCategory(String category) {
		this.category = category;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public BigDecimal getAmount() {
		return amount;
	}

	public void setAmount(BigDecimal amount) {
		this.amount = amount;
	}

	public String getReceiptNo() {
		return receiptNo;
	}

	public void setReceiptNo(String receiptNo) {
		this.receiptNo = receiptNo;
	}

	public String getReason() {
		return reason;
	}

	public void setReason(String reason) {
		this.reason = reason;
	}

	public String getApprovedBy() {
		return approvedBy;
	}

	public void setApprovedBy(String approvedBy) {
		this.approvedBy = approvedBy;
	}

	public String getProductId() {
		return productId;
	}

	public void setProductId(String productId) {
		this.productId = productId;
	}

	public String getProductSerial() {
		return productSerial;
	}

	public void setProductSerial(String productSerial) {
		this.productSerial = productSerial;
	}

	public String getProductName() {
		return productName;
	}

	public void setProductName(String productName) {
		this.productName = productName;
	}

	public String getUnit() {
		return unit;
	}

	public void setUnit(String unit) {
		this.unit = unit;
	}

	public BigDecimal getQuantity() {
		return quantity;
	}

	public void setQuantity(BigDecimal quantity) {
		this.quantity = quantity;
	}

	public BigDecimal getUnitCost() {
		return unitCost;
	}

	public void setUnitCost(BigDecimal unitCost) {
		this.unitCost = unitCost;
	}

	public Boolean getOverThreshold() {
		return overThreshold;
	}

	public void setOverThreshold(Boolean overThreshold) {
		this.overThreshold = overThreshold;
	}

	public String getStatus() {
		return status;
	}

	public void setStatus(String status) {
		this.status = status;
	}

	public String getCreatedBy() {
		return createdBy;
	}

	public void setCreatedBy(String createdBy) {
		this.createdBy = createdBy;
	}

	public String getCreatedByName() {
		return createdByName;
	}

	public void setCreatedByName(String createdByName) {
		this.createdByName = createdByName;
	}

	public LocalDateTime getRegistered() {
		return registered;
	}

	public void setRegistered(LocalDateTime registered) {
		this.registered = registered;
	}

	public String getVoidedBy() {
		return voidedBy;
	}

	public void setVoidedBy(String voidedBy) {
		this.voidedBy = voidedBy;
	}

	public String getVoidedByName() {
		return voidedByName;
	}

	public void setVoidedByName(String voidedByName) {
		this.voidedByName = voidedByName;
	}

	public LocalDateTime getVoidedAt() {
		return voidedAt;
	}

	public void setVoidedAt(LocalDateTime voidedAt) {
		this.voidedAt = voidedAt;
	}

	public String getVoidReason() {
		return voidReason;
	}

	public void setVoidReason(String voidReason) {
		this.voidReason = voidReason;
	}

	public List<String> getWarnings() {
		return warnings;
	}

	public void setWarnings(List<String> warnings) {
		this.warnings = warnings;
	}

	public BigDecimal getPettyCashBalance() {
		return pettyCashBalance;
	}

	public void setPettyCashBalance(BigDecimal pettyCashBalance) {
		this.pettyCashBalance = pettyCashBalance;
	}

}
