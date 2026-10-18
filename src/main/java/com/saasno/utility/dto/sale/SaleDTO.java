package com.saasno.utility.dto.sale;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

// Transaksi, atau pratinjau perhitungan keranjang (belum tersimpan: id, serial, dan data keputusan kosong).
@JsonIgnoreProperties(ignoreUnknown = true)
public class SaleDTO {

	private String id;
	private String serial;
	private String branchId;
	private String branchName;
	private String customerId;
	private String customerName;
	private String status;
	private List<SaleItemDTO> items;
	private BigDecimal subtotal;
	private String promoId;
	private String promoName;
	private BigDecimal promoDiscount;
	private BigDecimal manualDiscount;
	// Potongan dari voucher dan poin (tidak butuh persetujuan); terpisah dari diskon manual.
	private BigDecimal rewardDiscount;
	// Bagian tagihan yang masih berupa piutang saat transaksi (penjualan tempo).
	private BigDecimal creditAmount;
	// ONLINE | OFFLINE.
	private String source;
	private String voucherCode;
	private Long pointsUsed;
	private Long pointsEarned;
	private String manualDiscountReason;
	private java.util.List<SaleChargeDTO> charges;
	private BigDecimal chargeTotal;
	private BigDecimal total;
	private BigDecimal paid;
	private BigDecimal changeAmount;
	private Boolean requiresApproval;
	private BigDecimal manualDiscountLimitPercent;
	private String createdBy;
	private String createdByName;
	private LocalDateTime registered;
	private String decidedBy;
	private String decidedByName;
	private LocalDateTime decidedAt;
	private String decisionNote;
	private String voidedBy;
	private String voidedByName;
	private LocalDateTime voidedAt;
	private String voidReason;

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

	public String getCustomerId() {
		return customerId;
	}

	public void setCustomerId(String customerId) {
		this.customerId = customerId;
	}

	public String getCustomerName() {
		return customerName;
	}

	public void setCustomerName(String customerName) {
		this.customerName = customerName;
	}

	public String getStatus() {
		return status;
	}

	public void setStatus(String status) {
		this.status = status;
	}

	public List<SaleItemDTO> getItems() {
		return items;
	}

	public void setItems(List<SaleItemDTO> items) {
		this.items = items;
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

	public java.util.List<SaleChargeDTO> getCharges() {
		return charges;
	}

	public void setCharges(java.util.List<SaleChargeDTO> charges) {
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

	public Boolean getRequiresApproval() {
		return requiresApproval;
	}

	public void setRequiresApproval(Boolean requiresApproval) {
		this.requiresApproval = requiresApproval;
	}

	public BigDecimal getManualDiscountLimitPercent() {
		return manualDiscountLimitPercent;
	}

	public void setManualDiscountLimitPercent(BigDecimal manualDiscountLimitPercent) {
		this.manualDiscountLimitPercent = manualDiscountLimitPercent;
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

	public BigDecimal getRewardDiscount() {
		return rewardDiscount;
	}

	public void setRewardDiscount(BigDecimal rewardDiscount) {
		this.rewardDiscount = rewardDiscount;
	}

	public String getVoucherCode() {
		return voucherCode;
	}

	public void setVoucherCode(String voucherCode) {
		this.voucherCode = voucherCode;
	}

	public Long getPointsUsed() {
		return pointsUsed;
	}

	public void setPointsUsed(Long pointsUsed) {
		this.pointsUsed = pointsUsed;
	}

	public Long getPointsEarned() {
		return pointsEarned;
	}

	public void setPointsEarned(Long pointsEarned) {
		this.pointsEarned = pointsEarned;
	}

	public BigDecimal getCreditAmount() {
		return creditAmount;
	}

	public void setCreditAmount(BigDecimal creditAmount) {
		this.creditAmount = creditAmount;
	}

	public String getSource() {
		return source;
	}

	public void setSource(String source) {
		this.source = source;
	}
}
