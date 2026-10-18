package com.saasno.entity;

import com.saasno.utility.generator.ID;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;

// Retur penjualan (tunai); mengembalikan stok.
@Entity
@Table(schema = "public", name = "sale_return")
public class SaleReturnEntity {

	@Id
	@Column(name = "id", length = 64, nullable = false, updatable = false)
	private String id;

	@Column(name = "serial", length = 40, nullable = false, updatable = false, unique = true)
	private String serial;

	@Column(name = "sale_id", length = 64, nullable = false, updatable = false)
	private String saleId;

	@Column(name = "branch_id", length = 64, nullable = false, updatable = false)
	private String branchId;

	@Column(name = "reason", length = 500, nullable = false, updatable = false)
	private String reason;

	@Column(name = "refund_total", precision = 19, scale = 2, nullable = false, updatable = false)
	private BigDecimal refundTotal;

	@Column(name = "created_by", length = 64, nullable = false, updatable = false)
	private String createdBy;

	@Column(name = "registered", nullable = false, updatable = false)
	private LocalDateTime registered;

	// Bagian refund yang mengurangi piutang customer (bukan uang tunai keluar).
	@Column(name = "credit_offset", precision = 19, scale = 2, nullable = false, updatable = false)
	private BigDecimal creditOffset = BigDecimal.ZERO;

	@Column(name = "promo_adjustment", precision = 19, scale = 2, nullable = false, updatable = false)
	private BigDecimal promoAdjustment;

	public SaleReturnEntity() {
		this.id = ID.uuid();
		this.promoAdjustment = BigDecimal.ZERO;
	}

	@PrePersist
	public void beforeInsert() {
		this.registered = LocalDateTime.now();
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

	public String getSaleId() {
		return saleId;
	}

	public void setSaleId(String saleId) {
		this.saleId = saleId;
	}

	public String getBranchId() {
		return branchId;
	}

	public void setBranchId(String branchId) {
		this.branchId = branchId;
	}

	public String getReason() {
		return reason;
	}

	public void setReason(String reason) {
		this.reason = reason;
	}

	public BigDecimal getRefundTotal() {
		return refundTotal;
	}

	public void setRefundTotal(BigDecimal refundTotal) {
		this.refundTotal = refundTotal;
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

	public BigDecimal getPromoAdjustment() {
		return promoAdjustment;
	}

	public void setPromoAdjustment(BigDecimal promoAdjustment) {
		this.promoAdjustment = promoAdjustment;
	}

	public BigDecimal getCreditOffset() {
		return creditOffset;
	}

	public void setCreditOffset(BigDecimal creditOffset) {
		this.creditOffset = creditOffset;
	}
}
