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

// Transaksi penjualan tunai. Tidak pernah dihapus; hanya status dan data keputusan/pembatalan yang berubah.
@Entity
@Table(schema = "public", name = "sale")
public class SaleEntity {

	public static final String COMPLETED = "COMPLETED";
	public static final String WAITING_APPROVAL = "WAITING_APPROVAL";
	public static final String REJECTED = "REJECTED";
	public static final String CANCELLED = "CANCELLED";
	public static final String VOIDED = "VOIDED";

	@Id
	@Column(name = "id", length = 64, nullable = false, updatable = false)
	private String id;

	@Column(name = "serial", length = 40, nullable = false, updatable = false, unique = true)
	private String serial;

	@Column(name = "branch_id", length = 64, nullable = false, updatable = false)
	private String branchId;

	@Column(name = "customer_id", length = 64, updatable = false)
	private String customerId;

	@Column(name = "status", length = 20, nullable = false)
	private String status;

	@Column(name = "subtotal", precision = 19, scale = 2, nullable = false, updatable = false)
	private BigDecimal subtotal;

	@Column(name = "promo_id", length = 64, updatable = false)
	private String promoId;

	@Column(name = "promo_name", length = 256, updatable = false)
	private String promoName;

	@Column(name = "promo_discount", precision = 19, scale = 2, nullable = false, updatable = false)
	private BigDecimal promoDiscount;

	@Column(name = "manual_discount", precision = 19, scale = 2, nullable = false, updatable = false)
	private BigDecimal manualDiscount;

	@Column(name = "manual_discount_reason", length = 500, updatable = false)
	private String manualDiscountReason;

	// Biaya lain-lain (pajak dll.) yang dibekukan saat transaksi: JSON array dan jumlahnya (termasuk yang sudah ada di harga).
	@Column(name = "charges", columnDefinition = "text", nullable = false, updatable = false)
	private String charges;

	@Column(name = "charge_total", precision = 19, scale = 2, nullable = false, updatable = false)
	private BigDecimal chargeTotal;

	@Column(name = "total", precision = 19, scale = 2, nullable = false, updatable = false)
	private BigDecimal total;

	@Column(name = "paid", precision = 19, scale = 2, nullable = false, updatable = false)
	private BigDecimal paid;

	@Column(name = "change_amount", precision = 19, scale = 2, nullable = false, updatable = false)
	private BigDecimal changeAmount;

	@Column(name = "request_key", length = 64, updatable = false)
	private String requestKey;

	@Column(name = "created_by", length = 64, nullable = false, updatable = false)
	private String createdBy;

	@Column(name = "registered", nullable = false, updatable = false)
	private LocalDateTime registered;

	@Column(name = "decided_by", length = 64)
	private String decidedBy;

	@Column(name = "decided_at")
	private LocalDateTime decidedAt;

	@Column(name = "decision_note", length = 500)
	private String decisionNote;

	@Column(name = "voided_by", length = 64)
	private String voidedBy;

	@Column(name = "voided_at")
	private LocalDateTime voidedAt;

	@Column(name = "void_reason", length = 500)
	private String voidReason;

	@Column(name = "modificated", nullable = false)
	private LocalDateTime modificated;

	@Column(name = "promo_scope", length = 16, updatable = false)
	private String promoScope;

	@Column(name = "promo_product_id", length = 64, updatable = false)
	private String promoProductId;

	@Column(name = "promo_condition_type", length = 16, updatable = false)
	private String promoConditionType;

	@Column(name = "promo_min_value", precision = 19, scale = 6, updatable = false)
	private BigDecimal promoMinValue;

	@Column(name = "promo_reward_type", length = 16, updatable = false)
	private String promoRewardType;

	@Column(name = "promo_discount_value", precision = 19, scale = 2, updatable = false)
	private BigDecimal promoDiscountValue;

	@Column(name = "promo_value", precision = 19, scale = 2, updatable = false)
	private BigDecimal promoValue;

	public SaleEntity() {
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

	public String getCustomerId() {
		return customerId;
	}

	public void setCustomerId(String customerId) {
		this.customerId = customerId;
	}

	public String getStatus() {
		return status;
	}

	public void setStatus(String status) {
		this.status = status;
	}

	public BigDecimal getSubtotal() {
		return subtotal;
	}

	public void setSubtotal(BigDecimal subtotal) {
		this.subtotal = subtotal;
	}

	public String getPromoId() {
		return promoId;
	}

	public void setPromoId(String promoId) {
		this.promoId = promoId;
	}

	public String getPromoName() {
		return promoName;
	}

	public void setPromoName(String promoName) {
		this.promoName = promoName;
	}

	public BigDecimal getPromoDiscount() {
		return promoDiscount;
	}

	public void setPromoDiscount(BigDecimal promoDiscount) {
		this.promoDiscount = promoDiscount;
	}

	public BigDecimal getManualDiscount() {
		return manualDiscount;
	}

	public void setManualDiscount(BigDecimal manualDiscount) {
		this.manualDiscount = manualDiscount;
	}

	public String getManualDiscountReason() {
		return manualDiscountReason;
	}

	public void setManualDiscountReason(String manualDiscountReason) {
		this.manualDiscountReason = manualDiscountReason;
	}

	public String getCharges() {
		return charges;
	}

	public void setCharges(String charges) {
		this.charges = charges;
	}

	public BigDecimal getChargeTotal() {
		return chargeTotal;
	}

	public void setChargeTotal(BigDecimal chargeTotal) {
		this.chargeTotal = chargeTotal;
	}

	public BigDecimal getTotal() {
		return total;
	}

	public void setTotal(BigDecimal total) {
		this.total = total;
	}

	public BigDecimal getPaid() {
		return paid;
	}

	public void setPaid(BigDecimal paid) {
		this.paid = paid;
	}

	public BigDecimal getChangeAmount() {
		return changeAmount;
	}

	public void setChangeAmount(BigDecimal changeAmount) {
		this.changeAmount = changeAmount;
	}

	public String getRequestKey() {
		return requestKey;
	}

	public void setRequestKey(String requestKey) {
		this.requestKey = requestKey;
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

	public String getPromoScope() {
		return promoScope;
	}

	public void setPromoScope(String promoScope) {
		this.promoScope = promoScope;
	}

	public String getPromoProductId() {
		return promoProductId;
	}

	public void setPromoProductId(String promoProductId) {
		this.promoProductId = promoProductId;
	}

	public String getPromoConditionType() {
		return promoConditionType;
	}

	public void setPromoConditionType(String promoConditionType) {
		this.promoConditionType = promoConditionType;
	}

	public BigDecimal getPromoMinValue() {
		return promoMinValue;
	}

	public void setPromoMinValue(BigDecimal promoMinValue) {
		this.promoMinValue = promoMinValue;
	}

	public String getPromoRewardType() {
		return promoRewardType;
	}

	public void setPromoRewardType(String promoRewardType) {
		this.promoRewardType = promoRewardType;
	}

	public BigDecimal getPromoDiscountValue() {
		return promoDiscountValue;
	}

	public void setPromoDiscountValue(BigDecimal promoDiscountValue) {
		this.promoDiscountValue = promoDiscountValue;
	}

	public BigDecimal getPromoValue() {
		return promoValue;
	}

	public void setPromoValue(BigDecimal promoValue) {
		this.promoValue = promoValue;
	}

}
