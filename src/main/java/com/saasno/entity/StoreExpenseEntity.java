package com.saasno.entity;

import com.saasno.utility.generator.ID;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;

// Catatan biaya operasional toko. CASH = beli perlengkapan dengan uang kas kecil; STOCK_USE = memakai stok sendiri
// (tidak menyentuh kas kecil; nilai = jumlah x harga pokok). Tidak dihapus, hanya dibatalkan (VOIDED) dengan alasan.
@Entity
@Table(schema = "public", name = "store_expense")
public class StoreExpenseEntity {

	public static final String CASH = "CASH";
	public static final String STOCK_USE = "STOCK_USE";
	public static final String RECORDED = "RECORDED";
	public static final String VOIDED = "VOIDED";

	@Id
	@Column(name = "id", length = 64, nullable = false, updatable = false)
	private String id;

	@Column(name = "serial", length = 40, nullable = false, updatable = false, unique = true)
	private String serial;

	@Column(name = "branch_id", length = 64, nullable = false, updatable = false)
	private String branchId;

	@Column(name = "kind", length = 12, nullable = false, updatable = false)
	private String kind;

	@Column(name = "category", length = 32, nullable = false, updatable = false)
	private String category;

	@Column(name = "description", length = 256, nullable = false, updatable = false)
	private String description;

	@Column(name = "amount", precision = 19, scale = 2, nullable = false, updatable = false)
	private BigDecimal amount;

	@Column(name = "receipt_no", length = 64, updatable = false)
	private String receiptNo;

	@Column(name = "reason", length = 500, nullable = false, updatable = false)
	private String reason;

	@Column(name = "approved_by", length = 128, updatable = false)
	private String approvedBy;

	@Column(name = "product_id", length = 64, updatable = false)
	private String productId;

	@Column(name = "quantity", precision = 19, scale = 6, updatable = false)
	private BigDecimal quantity;

	@Column(name = "unit_cost", precision = 19, scale = 2, updatable = false)
	private BigDecimal unitCost;

	@Column(name = "over_threshold", nullable = false, updatable = false)
	private boolean overThreshold;

	@Column(name = "status", length = 12, nullable = false)
	private String status;

	@Column(name = "created_by", length = 64, nullable = false, updatable = false)
	private String createdBy;

	@Column(name = "registered", nullable = false, updatable = false)
	private LocalDateTime registered;

	@Column(name = "voided_by", length = 64)
	private String voidedBy;

	@Column(name = "voided_at")
	private LocalDateTime voidedAt;

	@Column(name = "void_reason", length = 500)
	private String voidReason;

	@Column(name = "modificated", nullable = false)
	private LocalDateTime modificated;

	public StoreExpenseEntity() {
		this.id = ID.uuid();
		this.status = RECORDED;
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

	public boolean isOverThreshold() {
		return overThreshold;
	}

	public void setOverThreshold(boolean overThreshold) {
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

	public LocalDateTime getModificated() {
		return modificated;
	}

	public void setModificated(LocalDateTime modificated) {
		this.modificated = modificated;
	}

}
