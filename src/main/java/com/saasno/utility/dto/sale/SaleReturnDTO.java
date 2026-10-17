package com.saasno.utility.dto.sale;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

// Permintaan memakai saleId, reason, dan items; sisanya diisi sistem. promoAdjustment = bagian nilai kembali yang
// ditahan karena syarat promo tidak terpenuhi lagi setelah retur.
@JsonIgnoreProperties(ignoreUnknown = true)
public class SaleReturnDTO {

	private String id;
	private String serial;
	private String saleId;
	private String saleSerial;
	private String branchId;
	private String branchName;
	private String reason;
	private BigDecimal refundTotal;
	private String createdBy;
	private String createdByName;
	private LocalDateTime registered;
	private List<SaleReturnItemDTO> items;
	private BigDecimal promoAdjustment;

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

	public String getSaleSerial() {
		return saleSerial;
	}

	public void setSaleSerial(String saleSerial) {
		this.saleSerial = saleSerial;
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

	public List<SaleReturnItemDTO> getItems() {
		return items;
	}

	public void setItems(List<SaleReturnItemDTO> items) {
		this.items = items;
	}

	public BigDecimal getPromoAdjustment() {
		return promoAdjustment;
	}

	public void setPromoAdjustment(BigDecimal promoAdjustment) {
		this.promoAdjustment = promoAdjustment;
	}

}
